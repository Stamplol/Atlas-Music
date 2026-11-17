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


def _sync_id3_size(blob, offset):
    """Compute ID3v2 declared size (synchsafe) at offset, return (data_size, total_size)."""
    def _s(b):
        return (b[0] & 0x7f) << 21 | (b[1] & 0x7f) << 14 | (b[2] & 0x7f) << 7 | (b[3] & 0x7f)
    hdr_size = 10  # ID3v2 simple header
    if blob[offset:offset + 3] != b"ID3":
        return None
    size = _s(blob[offset + 6:offset + 10])
    total = hdr_size + size
    # unsynchronisation/extended header adds bytes — approximate via the
    # actual structure; for ffmpeg-wrapped files this fits tightly.
    return size, total


def _strip_id3_preamble(blob):
    """If blob looks like an MP4 with an ID3v2 preamble (ffmpeg-style), drop the preamble."""
    if not blob or len(blob) < 16 or blob[:3] != b"ID3":
        return None
    # With an FFmpeg-wrapped ffmpeg MP4, ftyp starts after the ID3 block; scan forward.
    idx = blob.find(b"ftyp")
    if idx == -1 or idx < 20:
        return None
    # Keep the 4-byte atom length prefix immediately before 'ftyp' (normally '00 00 00 1C').
    return blob[idx - 4:]

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
        # SoundCloud exposes the artist's reuse grant here; YouTube reports all-rights-reserved.
        # Atlas only downloads tracks licensed for redistribution (CC) or artist-enabled.
        "license": info.get("license"),
        "genres": info.get("genres"),
        "upload_date": (info.get("upload_date") or "")[:8] or None,
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
            "extract_flat": "in_playlist",
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
        # AuraFetch robustness: skip private/deleted instead of aborting, keep going
        # on partial fragments, tolerate windows-unsafe titles.
        opts = {
            "format": fmt,
            "outtmpl": os.path.join(out_dir, "%(title).120s [%(id)s].%(ext)s"),
            "quiet": True,
            "no_warnings": True,
            "noplaylist": True,
            "ignoreerrors": True,
            "ignore_no_formats_error": True,
            "skip_unavailable_fragments": True,
            "windowsfilenames": True,
            "concurrent_fragment_downloads": 4,
            "http_chunk_size": 10 * 1024 * 1024,
            "socket_timeout": 30,
            "retries": 10,
            "fragment_retries": 10,
            "progress_hooks": [hook],
            "overwrites": False,
            "continuedl": True,
        }
        try:
            with yt_dlp.YoutubeDL(opts) as ydl:
                info = ydl.extract_info(url, download=True)
        except Exception as first_exc:
            msg = (str(first_exc) or "").lower()
            cancelled = "cancelled by user" in msg
            if cancelled:
                raise
            if "requested format is not available" in msg or "format is not available" in msg:
                # AuraFetch-style second chance: the specific format chain died, fall back wholesale.
                opts_fb = dict(opts)
                opts_fb["format"] = "bestaudio/best"
                with yt_dlp.YoutubeDL(opts_fb) as ydl:
                    info = ydl.extract_info(url, download=True)
            else:
                raise
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


def search(query, provider, limit=6):
    """In-app search across YouTube / SoundCloud — returns flat entries only.

    Query never leaves yt-dlp's own search-key parsing; it is never executed and
    never joined into a raw URL string. Empty/None-safe.
    """
    clean = (query or "").strip()[:120]
    if not clean:
        return json.dumps({"ok": False, "error": "Type something to search."})
    try:
        n = max(1, min(int(limit or 6), 10))
    except (TypeError, ValueError):
        n = 6
    if provider == "youtube":
        key = f"ytsearch{n}:{clean}"
    elif provider == "soundcloud":
        key = f"scsearch{n}:{clean}"
    else:
        return json.dumps({"ok": False, "error": "Choose YouTube or SoundCloud first."})
    try:
        yt_dlp = _import_ytdlp()
        opts = {
            "quiet": True,
            "no_warnings": True,
            "ignoreerrors": True,
            "socket_timeout": 15,
            "retries": 1,
            "extract_flat": "in_playlist",
            "playlistend": n,
        }
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(key, download=False)
    except Exception as exc:
        msg = str(exc) or "Search failed"
        return json.dumps({"ok": False, "error": msg[:300]})

    entries = []
    for e in (info or {}).get("entries") or []:
        if not e:
            continue
        url = e.get("webpage_url") or e.get("url")
        vid_id = e.get("id")
        if not url and vid_id and provider == "youtube":
            url = f"https://www.youtube.com/watch?v={vid_id}"
        if not url:
            continue
        thumbs = e.get("thumbnails") or []
        thumb = None
        if isinstance(thumbs, list) and thumbs:
            try:
                thumb = max(thumbs, key=lambda t: (t.get("width") or 0)).get("url")
            except Exception:
                thumb = thumbs[-1].get("url") if isinstance(thumbs[-1], dict) else None
        if not thumb:
            thumb = e.get("thumbnail")

        entries.append({
            "id": str(vid_id or url),
            "title": e.get("title") or "Unknown",
            "uploader": e.get("uploader") or e.get("channel") or "Unknown artist",
            "duration": e.get("duration"),
            "thumbnail": thumb,
            "url": url,
        })
    return json.dumps({"ok": True, "provider": provider, "query": clean, "entries": entries})

def list_playlist_entries(url):
    """List videos in a YouTube playlist (flat). Never downloads media.

    Returns JSON {ok, count, title, uploader, entries:[{id,title,duration,thumbnail}]}.
    Private/deleted placeholders are skipped, matching AuraFetch's batch logic.
    """
    try:
        yt_dlp = _import_ytdlp()
        skip_titles = {"[private video]", "[deleted video]", "private video", "deleted video"}
        opts = {
            "quiet": True,
            "no_warnings": True,
            "skip_download": True,
            "extract_flat": True,
            "ignoreerrors": True,
            "socket_timeout": 15,
            "retries": 2,
        }
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=False)
        if not info:
            return json.dumps({"ok": False, "error": "Playlist appears empty or unavailable."})
        if "entries" not in info:
            # Single video — treat as list of one so the caller can always batch-upgrade
            entries = [{"id": info.get("id"), "title": info.get("title"), "url": info.get("webpage_url") or url,
                        "duration": info.get("duration"), "thumbnail": info.get("thumbnail")}]
            return json.dumps({"ok": True, "is_playlist": False, "count": 1,
                               "title": info.get("title"), "uploader": info.get("uploader"),
                               "entries": entries})
        def _title_from_url(url):
            try:
                slug = [seg for seg in url.rstrip("/").split("/") if seg][-1]
                pretty = slug.replace("-", " ").replace("_", " ").strip()
                return pretty.title() if pretty else ""
            except Exception:
                return ""

        clean = []
        for e in info.get("entries") or []:
            if not e:
                continue
            if not (e.get("id") or e.get("url")):
                continue
            title = (e.get("title") or "").lower()
            if title in skip_titles:
                continue
            entry_url = e.get("url") or e.get("webpage_url") or (f"https://www.youtube.com/watch?v={e.get('id')}" if e.get("id") else None)
            if not entry_url:
                continue
            entry_title = e.get("title") or _title_from_url(entry_url) or "Track %d" % (len(clean) + 1)
            clean.append({"id": e.get("id"), "title": entry_title,
                          "url": entry_url, "duration": e.get("duration"),
                          "thumbnail": e.get("thumbnail")})
        return json.dumps({"ok": True, "is_playlist": True, "count": len(clean),
                           "title": info.get("title"), "uploader": info.get("uploader") or info.get("channel"),
                           "entries": clean[:200]})
    except Exception as exc:
        message = str(exc) or "Couldn't read the playlist."
        return json.dumps({"ok": False, "error": message[:400]})

def sha256_file(path):
    try:
        h = hashlib.sha256()
        with open(path, "rb") as fh:
            for chunk in iter(lambda: fh.read(65536), b""):
                h.update(chunk)
        return json.dumps({"ok": True, "sha256": h.hexdigest()})
    except Exception as exc:
        return json.dumps({"ok": False, "error": str(exc)[:200]})




def embed_cover(path, cover_url):
    """Download the supplied cover URL and embed it into the file via mutagen.

    Supports ID3v2 (MP3/AAC), FLAC pictures, Vorbis metadata_block_picture,
    and MP4/M4A covr atoms. Returns JSON {ok, error?}.
    """
    try:
        from mutagen import File as MutagenFile
        from mutagen.id3 import ID3, APIC
        from mutagen.mp4 import MP4, MP4Cover
        from mutagen.flac import FLAC, Picture
        from mutagen.oggvorbis import OggVorbis
        from mutagen.oggopus import OggOpus
        from mutagen.apev2 import APEv2File
        import urllib.request
    except Exception as exc:
        return json.dumps({"ok": False, "error": f"mutagen unavailable: {exc}"})

    if not path or not os.path.isfile(path):
        return json.dumps({"ok": False, "error": "file not found"})
    if not cover_url:
        return json.dumps({"ok": False, "error": "no cover url"})

    try:
        req = urllib.request.Request(cover_url, headers={"User-Agent": "AtlasMusic/1.0 (Android)"})
        with urllib.request.urlopen(req, timeout=15) as resp:
            image_bytes = resp.read()
        mime = resp.headers.get_content_type() if hasattr(resp.headers, 'get_content_type') else (resp.headers.get("Content-Type", "image/jpeg")).split(";")[0].strip().lower()
        if mime not in ("image/jpeg", "image/png", "image/webp"):
            mime = "image/jpeg"
    except Exception as exc:
        return json.dumps({"ok": False, "error": f"cover download failed: {exc}"})

    if len(image_bytes) < 1024:
        return json.dumps({"ok": False, "error": "cover too small / failed to fetch"})

    try:
        ext = os.path.splitext(path.lower())[1]
        if ext in (".mp3", ".aac"):
            # ID3 branch for MP3/AAC only — never for MP4/M4A.
            try:
                tags = ID3(path)
            except Exception:
                tags = ID3()
            tags.delall("APIC")
            tags.add(APIC(encoding=3, mime=mime, type=3, desc="Cover", data=image_bytes))
            tags.save(path, v2_version=3)
            return json.dumps({"ok": True})
        audio = MutagenFile(path)
        if audio is None:
            return json.dumps({"ok": False, "error": "unsupported audio format"})

        if isinstance(audio, FLAC):
            # FLAC: clear existing pictures, add one front cover
            audio.clear_pictures()
            pic = Picture()
            pic.type = 3  # front cover
            pic.mime = mime
            pic.desc = "Cover"
            pic.data = image_bytes
            audio.add_picture(pic)
            audio.save()
            return json.dumps({"ok": True})

        if isinstance(audio, (OggVorbis, OggOpus)):
            import base64
            audio.pop("metadata_block_picture", None)
            try:
                from mutagen.flac import Picture as _Pic
                pic = _Pic()
                pic.type = 3
                pic.mime = mime
                pic.desc = "Cover"
                pic.data = image_bytes
                raw = pic.write()
                b64 = base64.b64encode(raw).decode("ascii")
                audio["metadata_block_picture"] = [b64]
                audio.save()
                return json.dumps({"ok": True})
            except Exception as exc:
                return json.dumps({"ok": False, "error": f"ogg picture embed failed: {exc}"})

        if isinstance(audio, MP4):
            try:
                from mutagen.mp4 import MP4Cover
                covr_format = MP4Cover.FORMAT_JPEG if mime == "image/jpeg" else (MP4Cover.FORMAT_PNG if mime == "image/png" else MP4Cover.FORMAT_AUTO)
                audio["covr"] = [MP4Cover(image_bytes, imageformat=covr_format)]
                audio.save()
                return json.dumps({"ok": True})
            except Exception as exc:
                return json.dumps({"ok": False, "error": f"mp4 covr embed failed: {exc}"})

        try:
            APEv2File(path)
            return json.dumps({"ok": False, "error": "ape embedding not supported"})
        except Exception:
            pass
        if ext in (".m4a", ".mp4"):
            try:
                from mutagen.mp4 import MP4 as _Mp, MP4Cover as _MpC
                mp = _Mp(path)
                covr_format = _MpC.FORMAT_JPEG if mime == "image/jpeg" else _MpC.FORMAT_PNG
                mp["covr"] = [_MpC(image_bytes, imageformat=covr_format)]
                mp.save()
                return json.dumps({"ok": True})
            except Exception as exc:
                return json.dumps({"ok": False, "error": f"mp4/m4a covr embed failed: {exc}"})
        return json.dumps({"ok": False, "error": f"cover embedding not supported for {ext or 'this container'}"})
    except Exception as exc:
        return json.dumps({"ok": False, "error": traceback.format_exc(limit=2)})

def write_tags(path, title, artist, album, genre, year, track, disc):
    """Best-effort embedded tag rewrite via Mutagen. Returns JSON {ok, error?}."""
    try:
        from mutagen import File as MutagenFile
        from mutagen.mp3 import MP3
        from mutagen.mp4 import MP4 as MP4File
        from mutagen.flac import FLAC
        from mutagen.oggvorbis import OggVorbis
        from mutagen.oggopus import OggOpus
        from mutagen.id3 import ID3, TIT2, TPE1, TALB, TCON, TYER, TDRC, TRCK, TPOS
    except Exception:
        return json.dumps({"ok": False, "error": "mutagen unavailable"})
    try:
        if not path or not os.path.isfile(path):
            return json.dumps({"ok": False, "error": "file not found"})
        ext = os.path.splitext(path.lower())[1]
        # Rebuild ffmpeg-hybrid MP4s: ID3v2 pre-pended (sometimes by accident)
        # then the real MP4 story. Strip the preamble to make a valid MP4
        # that Android's MediaScanner/MP4 parsers can actually read.
        if ext not in (".mp3", ".aac"):
            try:
                with open(path, "rb") as fh:
                    prefix = fh.read(3)
                if prefix == b"ID3":
                    with open(path, "rb") as fh:
                        blob = fh.read()
                    stripped = _strip_id3_preamble(blob)
                    if stripped is not None and len(stripped) > 0:
                        with open(path, "wb") as fh:
                            fh.write(stripped)
            except Exception:
                pass
        # MP4/M4A first, even if ffmpeg-wrapped with an ID3v2 preamble — mutagen/mp4 reads it correctly.
        try:
            with open(path, "rb") as fh:
                head = fh.read(2 * 1024 * 1024)  # first 2 MB enough to find atom seeds
            if b"ftyp" in head or b"moov" in head:
                mp4 = MP4File(path)
                if title: mp4["\xa9nam"] = title
                if artist: mp4["\xa9ART"] = artist
                if album: mp4["\xa9alb"] = album
                if genre: mp4["\xa9gen"] = genre
                if year: mp4["\xa9day"] = str(year)
                if track: mp4["trkn"] = [(track, 0)]
                if disc: mp4["disk"] = [(disc, 0)]
                mp4.save()
                return json.dumps({"ok": True})
        except Exception as exc:
            # fall through to the ext dispatch instead of failing outright
            pass
        # MP3/AAC use ID3; ONLY for those. ID3 preamble inside MP4 is invalid for Android MediaScanner.
        if ext in (".mp3", ".aac"):
            # Inspect the second tag; if the file has MP4 atoms we refuse ID3-writing even for .aac extension.
            head_check = None
            try:
                with open(path, "rb") as fh:
                    fh.seek(min(0, 0))  # already at start
                    firstbytes = fh.read(16)
                    if b"ftyp" in firstbytes or b"moov" in firstbytes or firstbytes[4:8] == b"ftyp":
                        head_check = "mp4"
            except Exception:
                head_check = None
            if head_check == "mp4":
                return json.dumps({"ok": False, "error": "this .aac is actually mp4 — use mp4 path"})
            try:
                tags = ID3(path)
            except Exception:
                tags = ID3()
            def _set(frame):
                tags.delall(frame.FrameID)
                tags.add(frame)
            if title: _set(TIT2(encoding=3, text=title))
            if artist: _set(TPE1(encoding=3, text=artist))
            if album: _set(TALB(encoding=3, text=album))
            if genre: _set(TCON(encoding=3, text=genre))
            if year: _set(TYER(encoding=3, text=str(year)))
            if track: _set(TRCK(encoding=3, text=str(track)))
            if disc: _set(TPOS(encoding=3, text=str(disc)))
            tags.save(path, v2_version=3)
            return json.dumps({"ok": True})
        audio = MutagenFile(path)
        if audio is None:
            return json.dumps({"ok": False, "error": "unsupported audio format"})
        # Vorbis / Opus / FLAC
        if isinstance(audio, (FLAC, OggVorbis, OggOpus)):
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
        elif isinstance(audio, MP3) or isinstance(getattr(audio, "tags", None), ID3):
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
        else:
            return json.dumps({"ok": False, "error": f"embedded tags unsupported for {type(audio).__name__}"})
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
