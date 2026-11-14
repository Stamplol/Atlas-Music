"""Atlas Music — yt-dlp bridge.

Runs inside Chaquopy's embedded Python. Never executes arbitrary user input as code;
URLs are validated on the Kotlin side and http(s) only. All functions take plain
strings/ints and return JSON strings so failures are always serializable.
"""
import base64
import hashlib
import json
import os
import sys
import traceback

_bundled_version = None
_update_dir = None


def set_update_dir(path):
    """Point the import system at the runtime-updated yt-dlp package, if any.

    `path` is the directory that *contains* the yt_dlp package (the unpacked wheel
    root). sys.path entries must point at that parent — inserting the package
    directory itself would make `import yt_dlp` fail. Cached yt_dlp modules from an
    earlier import in this process are evicted so a post-update check sees the new code.
    """
    global _update_dir
    _update_dir = path
    # Always evict yt_dlp modules imported from a previous override.
    for mod in list(sys.modules):
        if mod == "yt_dlp" or mod.startswith("yt_dlp."):
            del sys.modules[mod]
    # Drop stale update entries from sys.path (any ytdlp-updates directory).
    sys.path[:] = [p for p in sys.path if "ytdlp-updates" not in p]
    if path and os.path.isdir(os.path.join(path, "yt_dlp")):
        if path not in sys.path:
            sys.path.insert(0, path)
    return True


def _import_ytdlp():
    import yt_dlp  # noqa: F401
    return yt_dlp


def ytdlp_version():
    try:
        from yt_dlp import version as _version
        return json.dumps({"ok": True, "version": getattr(_version, "__version__", "unknown"),
                           "path": getattr(_version, "__file__", "")})
    except Exception:
        return json.dumps({"ok": False, "error": traceback.format_exc(limit=3)})


def ytdlp_bundled_version():
    global _bundled_version
    if _bundled_version is None:
        _bundled_version = ytdlp_version()
    return _bundled_version


def _serialize(info, requested_format):
    formats = []
    for f in info.get("formats", []) or []:
        if f.get("acodec") in (None, "none"):
            continue
        formats.append({
            "format_id": f.get("format_id"),
            "ext": f.get("ext"),
            "acodec": f.get("acodec"),
            "abr": f.get("abr"),
            "asr": f.get("asr"),
            "filesize": f.get("filesize") or f.get("filesize_approx"),
            "note": f.get("format_note"),
        })
    return {
        "title": info.get("title"),
        "uploader": info.get("uploader") or info.get("channel") or info.get("uploader_id"),
        "duration": info.get("duration"),
        "thumbnail": info.get("thumbnail"),
        "webpage_url": info.get("webpage_url"),
        "is_playlist": bool(info.get("entries")),
        "playlist_count": len(info.get("entries") or []) or None,
        "formats": formats[:40],
    }


def fetch_info(url, format_preference="best"):
    """Fetch metadata only. Never downloads media."""
    try:
        yt_dlp = _import_ytdlp()
        opts = {
            "quiet": True,
            "no_warnings": True,
            "skip_download": True,
            "nocheckcertificate": False,
            "socket_timeout": 20,
            "extract_flat": False,
        }
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=False)
        if not info:
            return json.dumps({"ok": False, "error": "No metadata returned for this link."})
        payload = _serialize(info, format_preference)
        payload["ok"] = True
        return json.dumps(payload)
    except Exception as exc:  # yt-dlp raises many custom error types
        message = str(exc) or "Unknown downloader error"
        # Keep messages short for the UI; full detail goes to device logs.
        return json.dumps({"ok": False, "error": message[:400]})


def download(url, out_dir, format_pref, progress_callback=None, cancel_check=None):
    """Download audio only to out_dir/<title>.<ext>. Progress via Java callback.

    format_pref: 'opus'   -> bestaudio[acodec=opus]/bestaudio[ext=webm]/bestaudio
                 'm4a'    -> bestaudio[ext=m4a]/bestaudio[acodec=aac]/bestaudio
                 'best'   -> bestaudio/best
    """
    def hook(status):
        if progress_callback is None:
            return
        if cancel_check is not None and cancel_check():
            raise yt_dlp.utils.DownloadError("Cancelled by user")
        try:
            state = status.get("status")
            if state == "downloading":
                total = status.get("total_bytes") or status.get("total_bytes_estimate") or 0
                downloaded = status.get("downloaded_bytes") or 0
                speed = status.get("speed") or 0
                eta = status.get("eta") or 0
                progress_callback(
                    "downloading",
                    int(downloaded),
                    int(total),
                    int(speed),
                    int(eta),
                )
            elif state == "finished":
                progress_callback("finished", int(status.get("downloaded_bytes") or 0), 0, 0, 0)
        except Exception:
            # A broken hook must never kill the actual download.
            pass

    try:
        yt_dlp = _import_ytdlp()
        if format_pref == "opus":
            fmt = "bestaudio[acodec=opus]/bestaudio[ext=webm]/bestaudio/best"
        elif format_pref == "m4a":
            fmt = "bestaudio[ext=m4a]/bestaudio[acodec=aac]/bestaudio/best"
        else:
            fmt = "bestaudio/best"
        os.makedirs(out_dir, exist_ok=True)
        opts = {
            "format": fmt,
            "outtmpl": os.path.join(out_dir, "%(title).120s [%(id)s].%(ext)s"),
            "quiet": True,
            "no_warnings": True,
            "noplaylist": True,
            "socket_timeout": 30,
            "retries": 3,
            "progress_hooks": [hook],
            "overwrites": False,
            "continuedl": True,
        }
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=True)
        if not info:
            return json.dumps({"ok": False, "error": "Download returned no media."})
        # Resolve the filename that yt-dlp actually wrote.
        requested = (info.get("requested_downloads") or [{}])[0]
        path = requested.get("filepath") or requested.get("filename")
        if not path:
            candidates = sorted(
                (os.path.join(out_dir, f) for f in os.listdir(out_dir)),
                key=os.path.getmtime,
                reverse=True,
            )
            path = candidates[0] if candidates else None
        meta = _serialize(info, format_pref)
        return json.dumps({"ok": True, "path": path, "meta": meta})
    except Exception as exc:
        message = str(exc) or "Unknown downloader error"
        cancelled = "Cancelled by user" in message
        return json.dumps({"ok": False, "cancelled": cancelled, "error": message[:400]})


def sha256_file(path):
    try:
        h = hashlib.sha256()
        with open(path, "rb") as fh:
            for chunk in iter(lambda: fh.read(65536), b""):
                h.update(chunk)
        return json.dumps({"ok": True, "sha256": h.hexdigest()})
    except Exception as exc:
        return json.dumps({"ok": False, "error": str(exc)[:200]})


def write_tags(path, title, artist, album, genre, year, track, disc):
    """Best-effort embedded tag rewrite via Mutagen. Returns JSON {ok, error?}."""
    try:
        from mutagen import File as MutagenFile
        from mutagen.mp4 import MP4 as MP4File
        from mutagen.flac import FLAC
        from mutagen.oggvorbis import OggVorbis
        from mutagen.id3 import ID3, TIT2, TPE1, TALB, TCON, TYER, TDRC, TRCK, TPOS
    except Exception:
        return json.dumps({"ok": False, "error": "mutagen unavailable"})
    try:
        if not path or not os.path.isfile(path):
            return json.dumps({"ok": False, "error": "file not found"})
        audio = MutagenFile(path)
        if audio is None:
            return json.dumps({"ok": False, "error": "unsupported audio format"})
        # Vorbis / Ogg / FLAC
        if isinstance(audio, (FLAC, OggVorbis)):
            if title: audio["title"] = title
            if artist: audio["artist"] = artist
            if album: audio["album"] = album
            if genre: audio["genre"] = genre
            if year: audio["date"] = str(year)
            elif year == 0: audio.pop("date", None)
            if track: audio["tracknumber"] = str(track)
            if disc: audio["discnumber"] = str(disc)
            audio.save()
        elif isinstance(audio, MP4File):
            if title: audio["\xa9nam"] = title
            if artist: audio["\xa9ART"] = artist
            if album: audio["\xa9alb"] = album
            if genre: audio["\xa9gen"] = genre
            if year: audio["\xa9day"] = str(year)
            if track: audio["trkn"] = [(track, 0)]
            if disc: audio["disk"] = [(disc, 0)]
            audio.save()
        else:
            # ID3 (MP3 etc.)
            if audio.tags is None:
                audio.add_tags()
            def _set(frame):
                audio.tags.delall(frame.FrameID)
                audio.tags.add(frame)
            if title: _set(TIT2(encoding=3, text=title))
            if artist: _set(TPE1(encoding=3, text=artist))
            if album: _set(TALB(encoding=3, text=album))
            if genre: _set(TCON(encoding=3, text=genre))
            if year: _set(TYER(encoding=3, text=str(year)))
            if track: _set(TRCK(encoding=3, text=str(track)))
            if disc: _set(TPOS(encoding=3, text=str(disc)))
            audio.save()
        return json.dumps({"ok": True})
    except Exception:
        return json.dumps({"ok": False, "error": traceback.format_exc(limit=2)})


def read_lyrics(path):
    """Read embedded lyrics. Returns JSON {ok, source, lyrics_raw}.

    Probes ID3 USLT (MP3), Vorbis `lyrics` (Ogg/Vorbis/FLAC), and MP4 \xa9lyr.
    Unsynced text only — synced LRC goes through sidecar files.
    """
    try:
        from mutagen import File as MutagenFile
        from mutagen.mp3 import MP3
        from mutagen.id3 import ID3
        from mutagen.flac import FLAC
        from mutagen.oggvorbis import OggVorbis
        from mutagen.mp4 import MP4
    except Exception:
        return json.dumps({"ok": False, "error": "mutagen unavailable"})

    if not path or not os.path.isfile(path):
        return json.dumps({"ok": False, "error": "file not found"})

    text = None
    source = None
    try:
        file_id = os.path.splitext(path.lower())[1]
        if file_id in (".mp3", ".aac", ".wav"):
            try:
                tags = ID3(path)
                frames = tags.getall("USLT")
                if frames:
                    text = frames[0].text
                    source = "USLT"
            except Exception:
                pass
        candidate = MutagenFile(path)
        if text is None and candidate is not None:
            tags = getattr(candidate, "tags", None)
            # Vorbis-style comments (FLAC/Ogg/Opus): tags dict with custom keys
            if isinstance(tags, dict):
                for key in ("lyrics", "LYRICS", "lyric", "unsyncedlyrics", "syncedlyrics"):
                    if key in tags:
                        v = tags[key]
                        text = v[0] if isinstance(v, list) else str(v)
                        source = f"vorbis:{key}"
                        break
            # MP4 / m4a: \xa9lyr
            if text is None and hasattr(candidate, "keys"):
                if "\xa9lyr" in candidate:
                    v = candidate["\xa9lyr"]
                    text = v[0] if isinstance(v, list) else str(v)
                    source = "mp4"
    except Exception:
        return json.dumps({"ok": False, "error": traceback.format_exc(limit=2)})

    if not text:
        return json.dumps({"ok": False, "error": "no embedded lyrics"})
    return json.dumps({"ok": True, "source": source or "embedded", "lyrics_raw": text})
