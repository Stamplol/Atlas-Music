#!/usr/bin/env python3
"""Atlas iOS — generate a self-contained, idempotent Xcode project for ios/AtlasMusic.

Run from anywhere:  python ios/generate_xcodeproj.py
It enumerates every .swift / .ttf / .plist under ios/AtlasMusic (the app),
ios/AtlasMusicWidget (the Widget Extension) and ios/AtlasMusicTests (the unit
tests) and emits one project with THREE native targets: the AtlasMusic app, the
AtlasMusicWidget app-extension and the AtlasMusicTests unit-test bundle, all
signed-free for simulators with no Apple ID. A shared scheme builds all three and
runs the test bundle (⌘U). Re-running produces byte-identical output.
"""
from __future__ import annotations

import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SRC = ROOT / "AtlasMusic"
WIDGET_NAME = "AtlasMusicWidget"
WIDGET_SRC = ROOT / WIDGET_NAME
PROJECT = ROOT / "AtlasMusic.xcodeproj"

TARGET_NAME = "AtlasMusic"
BUNDLE_ID = "com.atlas.music"
WIDGET_BUNDLE_ID = "com.atlas.music.widget"
TEST_NAME = "AtlasMusicTests"
TEST_BUNDLE_ID = "com.atlas.music.tests"
TEST_SRC = ROOT / TEST_NAME
SCHEME_NAME = "AtlasMusic"


def oid(prefix: str, key: str) -> str:
    """Deterministic 24-hex object id so repeated runs produce byte-identical output."""
    return (prefix + hashlib.sha1(key.encode("utf-8")).hexdigest())[:24].upper()


def quote(value: str) -> str:
    if any(ch in value for ch in " ()<>\"") or not value:
        return '"%s"' % value
    return value


def rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


# ---------------------------------------------------------------- discovery
def scan(root: Path):
    """Return (swift, fonts, plists) found under root, sorted for determinism."""
    swifts: list[Path] = []
    fonts: list[Path] = []
    plists: list[Path] = []
    if root.is_dir():
        for path in sorted(root.rglob("*")):
            if not path.is_file():
                continue
            suffix = path.suffix.lower()
            if suffix == ".swift":
                swifts.append(path)
            elif suffix == ".ttf":
                fonts.append(path)
            elif suffix == ".plist":
                plists.append(path)
    return swifts, fonts, plists


app_swift, app_fonts, app_plists = scan(SRC)
widget_swift, _widget_fonts, widget_plists = scan(WIDGET_SRC)
test_swift, _test_fonts, _test_plists = scan(TEST_SRC)

# These are compiled into BOTH targets: the app mirrors state into the shared App
# Group suite, the extension renders it and the intents write commands back.
shared_swift = sorted((SRC / "Widgets").glob("*.swift"))

app_entitlement = ROOT / f"{TARGET_NAME}.entitlements"
widget_entitlement = ROOT / f"{WIDGET_NAME}.entitlements"

required_test_files = [TEST_SRC / "AtlasMusicTests.swift", TEST_SRC / "AudioDspTests.swift"]
missing = [p for p in shared_swift + required_test_files + [app_entitlement, widget_entitlement] if not p.is_file()]
if missing:
    raise SystemExit("Missing required files: " + ", ".join(rel(p) for p in missing))

asset_catalog = SRC / "Resources" / "Assets.xcassets"
if not asset_catalog.is_dir():
    raise SystemExit("Missing ios/AtlasMusic/Resources/Assets.xcassets")

app_info = next((p for p in app_plists if p.name == "Info.plist"), SRC / "Info.plist")
widget_info = next((p for p in widget_plists if p.name == "Info.plist"), WIDGET_SRC / "Info.plist")

file_type = {
    ".swift": "sourcecode.swift",
    ".ttf": "file",
    ".plist": "text.plist.xml",
    ".entitlements": "text.plist.xml",
}

# The entitlements are source files too, so Xcode shows them in the group tree.
# Test sources are included so they get file references and appear in the group tree.
all_files = app_swift + widget_swift + test_swift + app_fonts + app_plists + widget_plists + [
    app_entitlement, widget_entitlement
]

# Per-target compilation inputs. The shared Widgets/*.swift files deliberately
# appear in both lists; the widget-only @main file appears only in the extension
# so the app target keeps its single @main in AtlasMusicApp.swift.
app_sources = list(app_swift)
app_resources = list(app_fonts)
widget_sources = list(shared_swift) + list(widget_swift)
widget_resources: list[Path] = []
test_sources = list(test_swift)

# ---------------------------------------------------------------- ids
project_id = oid("P", "project")
target_id = oid("T", TARGET_NAME)
widget_target_id = oid("T", WIDGET_NAME)
test_target_id = oid("T", TEST_NAME)
main_group_id = oid("G", "ROOT")
products_group_id = oid("G", "Products")


def phase_ids(target: str) -> dict[str, str]:
    return {
        "sources": oid("S", f"{target}-sources"),
        "frameworks": oid("S", f"{target}-frameworks"),
        "resources": oid("S", f"{target}-resources"),
    }


app_phases = phase_ids(TARGET_NAME)
widget_phases = phase_ids(WIDGET_NAME)
test_phases = phase_ids(TEST_NAME)

# App embeds the extension and depends on it so ⌘R builds both in order.
embed_phase_id = oid("S", "app-embed-extensions")
app_dep_id = oid("D", "app-dep-widget")
widget_proxy_id = oid("X", "app-proxy-widget")
embed_build_id = oid("B", "embed-widget-appex")

# The unit-test bundle depends on the app target so ⌘U builds the host first.
test_dep_id = oid("D", "tests-dep-app")
test_proxy_id = oid("X", "tests-proxy-app")

project_config_list_id = oid("L", "project")
target_config_list_id = oid("L", f"target-{TARGET_NAME}")
widget_config_list_id = oid("L", f"target-{WIDGET_NAME}")
test_config_list_id = oid("L", f"target-{TEST_NAME}")

debug_project_cfg = oid("C", "project-debug")
release_project_cfg = oid("C", "project-release")
debug_target_cfg = oid("C", f"target-{TARGET_NAME}-debug")
release_target_cfg = oid("C", f"target-{TARGET_NAME}-release")
debug_widget_cfg = oid("C", f"target-{WIDGET_NAME}-debug")
release_widget_cfg = oid("C", f"target-{WIDGET_NAME}-release")
debug_test_cfg = oid("C", f"target-{TEST_NAME}-debug")
release_test_cfg = oid("C", f"target-{TEST_NAME}-release")

product_ref_id = oid("F", f"product-{TARGET_NAME}")
widget_product_ref_id = oid("F", f"product-{WIDGET_NAME}")
test_product_ref_id = oid("F", f"product-{TEST_NAME}")
catalog_ref_id = oid("F", rel(asset_catalog))
catalog_build_id = oid("B", rel(asset_catalog))

file_ref_ids: dict[str, str] = {rel(p): oid("F", rel(p)) for p in all_files}

build_file_ids: dict[tuple[str, str], str] = {}


def build_file_id(target: str, path: Path) -> str:
    """A file compiled into two targets needs two distinct PBXBuildFile objects."""
    key = (target, rel(path))
    if key not in build_file_ids:
        build_file_ids[key] = oid("B", f"{target}:{rel(path)}")
    return build_file_ids[key]


for target, paths in (
    (TARGET_NAME, app_sources + app_resources),
    (WIDGET_NAME, widget_sources + widget_resources),
    (TEST_NAME, test_sources),
):
    for path in paths:
        build_file_id(target, path)

source_keys = {(TARGET_NAME, rel(p)) for p in app_sources} | {
    (WIDGET_NAME, rel(p)) for p in widget_sources
} | {(TEST_NAME, rel(p)) for p in test_sources}

# ---------------------------------------------------------------- group tree
groups: dict[str, dict] = {
    main_group_id: {"name": "", "children": [], "parent": None},
}


def group_for(parts: tuple[str, ...]) -> str:
    if not parts:
        return main_group_id
    key = "GROUP:" + "/".join(parts)
    gid = oid("G", key)
    if gid not in groups:
        groups[gid] = {"name": parts[-1], "children": [], "parent": None}
        groups[gid]["parent"] = group_for(parts[:-1])
    return gid


for path in all_files:
    gid = group_for(tuple(path.relative_to(ROOT).parts[:-1]))
    groups[gid]["children"].append(("file", rel(path)))

# Asset catalog lives in the Resources group; add it as a folder reference.
catalog_parent = group_for(tuple(asset_catalog.relative_to(ROOT).parts[:-1]))
groups[catalog_parent]["children"].append(("catalog", rel(asset_catalog)))

for gid, info in groups.items():
    parent = info.get("parent")
    if parent in groups:
        groups[parent]["children"].append(("group", gid))

product_refs = {
    TARGET_NAME: product_ref_id,
    WIDGET_NAME: widget_product_ref_id,
    TEST_NAME: test_product_ref_id,
}
groups[main_group_id]["children"].append(("group", products_group_id))
groups[products_group_id] = {
    "name": "Products",
    "children": [("product", TARGET_NAME), ("product", WIDGET_NAME), ("product", TEST_NAME)],
    "parent": main_group_id,
}

group_lines: list[str] = []


def render_group(gid: str) -> None:
    info = groups[gid]
    group_lines.append("\t\t%s /* %s */ = {" % (gid, info["name"]))
    group_lines.append("\t\t\tisa = PBXGroup;")
    group_lines.append("\t\t\tchildren = (")
    for kind, value in info["children"]:
        if kind == "file":
            group_lines.append("\t\t\t\t%s /* %s */," % (file_ref_ids[value], Path(value).name))
        elif kind == "catalog":
            group_lines.append("\t\t\t\t%s /* Assets.xcassets */," % catalog_ref_id)
        elif kind == "product":
            group_lines.append("\t\t\t\t%s /* %s */," % (product_refs[value], value))
        else:
            group_lines.append("\t\t\t\t%s /* %s */," % (value, groups[value]["name"]))
    group_lines.append("\t\t\t);")
    group_lines.append("\t\t\tname = %s;" % quote(info["name"]))
    group_lines.append("\t\t\tsourceTree = \"<group>\";")
    group_lines.append("\t\t};")
    for kind, value in info["children"]:
        if kind == "group":
            render_group(value)


render_group(main_group_id)

# ---------------------------------------------------------------- sections
build_file_lines: list[str] = []
for (target, key), bid in sorted(build_file_ids.items()):
    label = "Sources" if (target, key) in source_keys else "Resources"
    fr = file_ref_ids[key]
    name = Path(key).name
    build_file_lines.append(
        f"\t\t{bid} /* {name} in {label} */ = {{isa = PBXBuildFile; fileRef = {fr} /* {name} */; }};"
    )
build_file_lines.append(
    f"\t\t{catalog_build_id} /* Assets.xcassets in Resources */ = "
    f"{{isa = PBXBuildFile; fileRef = {catalog_ref_id} /* Assets.xcassets */; }};"
)
build_file_lines.append(
    f"\t\t{embed_build_id} /* {WIDGET_NAME}.appex in Embed App Extensions */ = "
    f"{{isa = PBXBuildFile; fileRef = {widget_product_ref_id} /* {WIDGET_NAME}.appex */; "
    "settings = {ATTRIBUTES = (RemoveHeadersOnCopy, ); }; };"
)

file_ref_lines: list[str] = []
for path in all_files:
    key = rel(path)
    file_ref_lines.append(
        # Groups carry only a `name`, not a `path`, so a group-scoped file reference
        # resolves against the ios/ project root. Emit the full relative path, otherwise
        # Xcode looks for ios/AtlasWidgets.swift instead of ios/AtlasMusic/Widgets/AtlasWidgets.swift.
        f"\t\t{file_ref_ids[key]} /* {path.name} */ = {{isa = PBXFileReference; "
        f"lastKnownFileType = {file_type[path.suffix.lower()]}; path = {quote(rel(path))}; "
        'sourceTree = "<group>"; };'
    )
file_ref_lines.append(
    f"\t\t{catalog_ref_id} /* Assets.xcassets */ = {{isa = PBXFileReference; "
    f"lastKnownFileType = folder.assetcatalog; path = {quote(rel(asset_catalog))}; "
    'sourceTree = "<group>"; };'
)
file_ref_lines.append(
    f"\t\t{product_ref_id} /* {TARGET_NAME}.app */ = {{isa = PBXFileReference; "
    f"explicitFileType = wrapper.application; includeInIndex = 0; path = {TARGET_NAME}.app; "
    "sourceTree = BUILT_PRODUCTS_DIR; };"
)
file_ref_lines.append(
    f"\t\t{widget_product_ref_id} /* {WIDGET_NAME}.appex */ = {{isa = PBXFileReference; "
    f"explicitFileType = wrapper.app-extension; includeInIndex = 0; path = {WIDGET_NAME}.appex; "
    "sourceTree = BUILT_PRODUCTS_DIR; };"
)
file_ref_lines.append(
    f"\t\t{test_product_ref_id} /* {TEST_NAME}.xctest */ = {{isa = PBXFileReference; "
    f"explicitFileType = wrapper.cfbundle; includeInIndex = 0; path = {TEST_NAME}.xctest; "
    "sourceTree = BUILT_PRODUCTS_DIR; };"
)


def phase_files(target: str, paths: list[Path], label: str) -> str:
    return "\n".join(
        f"\t\t\t\t{build_file_id(target, p)} /* {p.name} in {label} */," for p in paths
    )


app_resources_lines = phase_files(TARGET_NAME, app_resources, "Resources")
app_resources_lines += ("\n" if app_resources_lines else "") + (
    f"\t\t\t\t{catalog_build_id} /* Assets.xcassets in Resources */,"
)

frameworks_section = "\n".join(
    f"""\t\t{ph["frameworks"]} /* Frameworks */ = {{
\t\t\tisa = PBXFrameworksBuildPhase;
\t\t\tbuildActionMask = 2147483647;
\t\t\tfiles = (
\t\t\t);
\t\t\trunOnlyForDeploymentPostprocessing = 0;
\t\t}};"""
    for ph in (app_phases, widget_phases, test_phases)
)

resources_section = f"""\t\t{app_phases["resources"]} /* Resources */ = {{
\t\t\tisa = PBXResourcesBuildPhase;
\t\t\tbuildActionMask = 2147483647;
\t\t\tfiles = (
{app_resources_lines}
\t\t\t);
\t\t\trunOnlyForDeploymentPostprocessing = 0;
\t\t}};
\t\t{widget_phases["resources"]} /* Resources */ = {{
\t\t\tisa = PBXResourcesBuildPhase;
\t\t\tbuildActionMask = 2147483647;
\t\t\tfiles = (
\t\t\t);
\t\t\trunOnlyForDeploymentPostprocessing = 0;
\t\t}};"""

sources_section = f"""\t\t{app_phases["sources"]} /* Sources */ = {{
\t\t\tisa = PBXSourcesBuildPhase;
\t\t\tbuildActionMask = 2147483647;
\t\t\tfiles = (
{phase_files(TARGET_NAME, app_sources, 'Sources')}
\t\t\t);
\t\t\trunOnlyForDeploymentPostprocessing = 0;
\t\t}};
\t\t{widget_phases["sources"]} /* Sources */ = {{
\t\t\tisa = PBXSourcesBuildPhase;
\t\t\tbuildActionMask = 2147483647;
\t\t\tfiles = (
{phase_files(WIDGET_NAME, widget_sources, 'Sources')}
\t\t\t);
\t\t\trunOnlyForDeploymentPostprocessing = 0;
\t\t}};
\t\t{test_phases["sources"]} /* Sources */ = {{
\t\t\tisa = PBXSourcesBuildPhase;
\t\t\tbuildActionMask = 2147483647;
\t\t\tfiles = (
{phase_files(TEST_NAME, test_sources, 'Sources')}
\t\t\t);
\t\t\trunOnlyForDeploymentPostprocessing = 0;
\t\t}};"""

native_targets_section = f"""\t\t{target_id} /* {TARGET_NAME} */ = {{
\t\t\tisa = PBXNativeTarget;
\t\t\tbuildConfigurationList = {target_config_list_id} /* Build configuration list for PBXNativeTarget "{TARGET_NAME}" */;
\t\t\tbuildPhases = (
\t\t\t\t{app_phases["sources"]} /* Sources */,
\t\t\t\t{app_phases["frameworks"]} /* Frameworks */,
\t\t\t\t{app_phases["resources"]} /* Resources */,
\t\t\t\t{embed_phase_id} /* Embed App Extensions */,
\t\t\t);
\t\t\tbuildRules = (
\t\t\t);
\t\t\tdependencies = (
\t\t\t\t{app_dep_id} /* PBXTargetDependency */,
\t\t\t);
\t\t\tname = {TARGET_NAME};
\t\t\tproductName = {TARGET_NAME};
\t\t\tproductReference = {product_ref_id} /* {TARGET_NAME}.app */;
\t\t\tproductType = "com.apple.product-type.application";
\t\t}};
\t\t{widget_target_id} /* {WIDGET_NAME} */ = {{
\t\t\tisa = PBXNativeTarget;
\t\t\tbuildConfigurationList = {widget_config_list_id} /* Build configuration list for PBXNativeTarget "{WIDGET_NAME}" */;
\t\t\tbuildPhases = (
\t\t\t\t{widget_phases["sources"]} /* Sources */,
\t\t\t\t{widget_phases["frameworks"]} /* Frameworks */,
\t\t\t\t{widget_phases["resources"]} /* Resources */,
\t\t\t);
\t\t\tbuildRules = (
\t\t\t);
\t\t\tdependencies = (
\t\t\t);
\t\t\tname = {WIDGET_NAME};
\t\t\tproductName = {WIDGET_NAME};
\t\t\tproductReference = {widget_product_ref_id} /* {WIDGET_NAME}.appex */;
\t\t\tproductType = "com.apple.product-type.app-extension";
\t\t}};
\t\t{test_target_id} /* {TEST_NAME} */ = {{
\t\t\tisa = PBXNativeTarget;
\t\t\tbuildConfigurationList = {test_config_list_id} /* Build configuration list for PBXNativeTarget "{TEST_NAME}" */;
\t\t\tbuildPhases = (
\t\t\t\t{test_phases["sources"]} /* Sources */,
\t\t\t\t{test_phases["frameworks"]} /* Frameworks */,
\t\t\t);
\t\t\tbuildRules = (
\t\t\t);
\t\t\tdependencies = (
\t\t\t\t{test_dep_id} /* PBXTargetDependency */,
\t\t\t);
\t\t\tname = {TEST_NAME};
\t\t\tproductName = {TEST_NAME};
\t\t\tproductReference = {test_product_ref_id} /* {TEST_NAME}.xctest */;
\t\t\tproductType = "com.apple.product-type.bundle.unit-test";
\t\t}};"""

project_section = f"""\t\t{project_id} /* Project object */ = {{
\t\t\tisa = PBXProject;
\t\t\tattributes = {{
\t\t\t\tBuildIndependentTargetsInParallel = 1;
\t\t\t\tLastUpgradeCheck = 1500;
\t\t\t\tTargetAttributes = {{
\t\t\t\t\t{target_id} = {{
\t\t\t\t\t\tCreatedOnToolsVersion = 15.0;
\t\t\t\t\t}};
\t\t\t\t\t{widget_target_id} = {{
\t\t\t\t\t\tCreatedOnToolsVersion = 15.0;
\t\t\t\t\t}};
\t\t\t\t\t{test_target_id} = {{
\t\t\t\t\t\tCreatedOnToolsVersion = 15.0;
\t\t\t\t\t\tTestTargetID = {target_id};
\t\t\t\t\t}};
\t\t\t\t}};
\t\t\t}};
\t\t\tbuildConfigurationList = {project_config_list_id} /* Build configuration list for PBXProject "{TARGET_NAME}" */;
\t\t\tcompatibilityVersion = "Xcode 14.0";
\t\t\tdevelopmentRegion = en;
\t\t\thasScannedForEncodings = 0;
\t\t\tknownRegions = (
\t\t\t\ten,
\t\t\t\tBase,
\t\t\t);
\t\t\tmainGroup = {main_group_id};
\t\t\tproductRefGroup = {products_group_id} /* Products */;
\t\t\tprojectDirPath = "";
\t\t\tprojectRoot = "";
\t\t\ttargets = (
\t\t\t\t{target_id} /* {TARGET_NAME} */,
\t\t\t\t{widget_target_id} /* {WIDGET_NAME} */,
\t\t\t\t{test_target_id} /* {TEST_NAME} */,
\t\t\t);
\t\t}};"""

# ---------------------------------------------------------------- build settings
app_common = f"""\t\t\t\tASSETCATALOG_COMPILER_APPICON_NAME = AppIcon;
\t\t\t\tCODE_SIGN_ENTITLEMENTS = {rel(app_entitlement)};
\t\t\t\tCODE_SIGNING_ALLOWED = NO;
\t\t\t\tCODE_SIGN_STYLE = Manual;
\t\t\t\tCURRENT_PROJECT_VERSION = 5;
\t\t\t\tDEVELOPMENT_TEAM = "";
\t\t\t\tENABLE_PREVIEWS = YES;
\t\t\t\tGENERATE_INFOPLIST_FILE = NO;
\t\t\t\tINFOPLIST_FILE = {rel(app_info)};
\t\t\t\tIPHONEOS_DEPLOYMENT_TARGET = 17.0;
\t\t\t\tLD_RUNPATH_SEARCH_PATHS = ( "$(inherited)", "@executable_path/Frameworks" );
\t\t\t\tMARKETING_VERSION = 1.0.4;
\t\t\t\tPRODUCT_BUNDLE_IDENTIFIER = {BUNDLE_ID};
\t\t\t\tPRODUCT_NAME = "$(TARGET_NAME)";
\t\t\t\tSWIFT_EMIT_LOC_STRINGS = YES;
\t\t\t\tSWIFT_VERSION = 5.10;
\t\t\t\tTARGETED_DEVICE_FAMILY = "1,2";"""

widget_common = f"""\t\t\t\tCODE_SIGN_ENTITLEMENTS = {rel(widget_entitlement)};
\t\t\t\tCODE_SIGNING_ALLOWED = NO;
\t\t\t\tCODE_SIGN_STYLE = Manual;
\t\t\t\tCURRENT_PROJECT_VERSION = 5;
\t\t\t\tDEVELOPMENT_TEAM = "";
\t\t\t\tGENERATE_INFOPLIST_FILE = NO;
\t\t\t\tINFOPLIST_FILE = {rel(widget_info)};
\t\t\t\tIPHONEOS_DEPLOYMENT_TARGET = 17.0;
\t\t\t\tLD_RUNPATH_SEARCH_PATHS = ( "$(inherited)", "@executable_path/Frameworks", "@executable_path/../../Frameworks" );
\t\t\t\tMARKETING_VERSION = 1.0.4;
\t\t\t\tPRODUCT_BUNDLE_IDENTIFIER = {WIDGET_BUNDLE_ID};
\t\t\t\tPRODUCT_BUNDLE_PACKAGE_TYPE = APPL;
\t\t\t\tPRODUCT_NAME = "$(TARGET_NAME)";
\t\t\t\tSKIP_INSTALL = YES;
\t\t\t\tSWIFT_ACTIVE_COMPILATION_CONDITIONS = ATLAS_WIDGET_EXTENSION;
\t\t\t\tSWIFT_EMIT_LOC_STRINGS = YES;
\t\t\t\tSWIFT_VERSION = 5.10;
\t\t\t\tTARGETED_DEVICE_FAMILY = "1,2";"""

# Unit-test bundle: hosted by the app via TEST_HOST/BUNDLE_LOADER, unsigned for
# simulators. No entitlements are required for the pure-Swift XCTest suite.
test_common = f"""\t\t\t\tBUNDLE_LOADER = "$(TEST_HOST)";
\t\t\t\tCODE_SIGNING_ALLOWED = NO;
\t\t\t\tCODE_SIGN_STYLE = Manual;
\t\t\t\tCURRENT_PROJECT_VERSION = 5;
\t\t\t\tDEVELOPMENT_TEAM = "";
\t\t\t\tGENERATE_INFOPLIST_FILE = YES;
\t\t\t\tIPHONEOS_DEPLOYMENT_TARGET = 17.0;
\t\t\t\tLD_RUNPATH_SEARCH_PATHS = ( "$(inherited)", "@executable_path/Frameworks", "@loader_path/Frameworks" );
\t\t\t\tMARKETING_VERSION = 1.0.4;
\t\t\t\tPRODUCT_BUNDLE_IDENTIFIER = {TEST_BUNDLE_ID};
\t\t\t\tPRODUCT_NAME = "$(TARGET_NAME)";
\t\t\t\tSWIFT_VERSION = 5.10;
\t\t\t\tTARGETED_DEVICE_FAMILY = "1,2";
\t\t\t\tTEST_HOST = "$(BUILT_PRODUCTS_DIR)/{TARGET_NAME}.app/{TARGET_NAME}";"""


def debug_variant(base: str) -> str:
    return base + """
\t\t\t\tDEBUG_INFORMATION_FORMAT = dwarf;
\t\t\t\tENABLE_TESTABILITY = YES;
\t\t\t\tGCC_OPTIMIZATION_LEVEL = 0;
\t\t\t\tONLY_ACTIVE_ARCH = YES;
\t\t\t\tSWIFT_OPTIMIZATION_LEVEL = "-Onone";"""


def release_variant(base: str) -> str:
    return base + """
\t\t\t\tDEBUG_INFORMATION_FORMAT = "dwarf-with-dsym";
\t\t\t\tENABLE_NS_ASSERTIONS = NO;
\t\t\t\tSWIFT_COMPILATION_MODE = wholemodule;
\t\t\t\tSWIFT_OPTIMIZATION_LEVEL = "-O";
\t\t\t\tVALIDATE_PRODUCT = YES;"""


def config(cid: str, settings: str, name: str) -> str:
    return f"""\t\t{cid} /* {name} */ = {{
\t\t\tisa = XCBuildConfiguration;
\t\t\tbuildSettings = {{
{settings}
\t\t\t}};
\t\t\tname = {name};
\t\t}};"""


project_debug_settings = """\t\t\t\tALWAYS_SEARCH_USER_PATHS = NO;
\t\t\t\tCLANG_ENABLE_MODULES = YES;
\t\t\t\tENABLE_STRICT_OBJC_MSGSEND = YES;
\t\t\t\tGCC_C_LANGUAGE_STANDARD = gnu17;
\t\t\t\tGCC_NO_COMMON_BLOCKS = YES;
\t\t\t\tSDKROOT = iphoneos;"""

configurations_section = "\n".join([
    config(debug_project_cfg, project_debug_settings, "Debug"),
    config(release_project_cfg, project_debug_settings, "Release"),
    config(debug_target_cfg, debug_variant(app_common), "Debug"),
    config(release_target_cfg, release_variant(app_common), "Release"),
    config(debug_widget_cfg, debug_variant(widget_common), "Debug"),
    config(release_widget_cfg, release_variant(widget_common), "Release"),
    config(debug_test_cfg, debug_variant(test_common), "Debug"),
    config(release_test_cfg, release_variant(test_common), "Release"),
])

config_lists_section = f"""\t\t{project_config_list_id} /* Build configuration list for PBXProject "{TARGET_NAME}" */ = {{
\t\t\tisa = XCConfigurationList;
\t\t\tbuildConfigurations = (
\t\t\t\t{debug_project_cfg} /* Debug */,
\t\t\t\t{release_project_cfg} /* Release */,
\t\t\t);
\t\t\tdefaultConfigurationIsVisible = 0;
\t\t\tdefaultConfigurationName = Release;
\t\t}};
\t\t{target_config_list_id} /* Build configuration list for PBXNativeTarget "{TARGET_NAME}" */ = {{
\t\t\tisa = XCConfigurationList;
\t\t\tbuildConfigurations = (
\t\t\t\t{debug_target_cfg} /* Debug */,
\t\t\t\t{release_target_cfg} /* Release */,
\t\t\t);
\t\t\tdefaultConfigurationIsVisible = 0;
\t\t\tdefaultConfigurationName = Release;
\t\t}};
\t\t{widget_config_list_id} /* Build configuration list for PBXNativeTarget "{WIDGET_NAME}" */ = {{
\t\t\tisa = XCConfigurationList;
\t\t\tbuildConfigurations = (
\t\t\t\t{debug_widget_cfg} /* Debug */,
\t\t\t\t{release_widget_cfg} /* Release */,
\t\t\t);
\t\t\tdefaultConfigurationIsVisible = 0;
\t\t\tdefaultConfigurationName = Release;
\t\t}};
\t\t{test_config_list_id} /* Build configuration list for PBXNativeTarget "{TEST_NAME}" */ = {{
\t\t\tisa = XCConfigurationList;
\t\t\tbuildConfigurations = (
\t\t\t\t{debug_test_cfg} /* Debug */,
\t\t\t\t{release_test_cfg} /* Release */,
\t\t\t);
\t\t\tdefaultConfigurationIsVisible = 0;
\t\t\tdefaultConfigurationName = Release;
\t\t}};"""

# ---------------------------------------------------------------- pbxproj template
# @@TOKEN@@ placeholders keep the OpenStep syntax free of f-string brace escaping.
PBX_TEMPLATE = """// !$*UTF8*$!
{
\tarchiveVersion = 1;
\tclasses = {
\t};
\tobjectVersion = 56;
\tobjects = {

/* Begin PBXBuildFile section */
@@BUILDFILES@@
/* End PBXBuildFile section */

/* Begin PBXContainerItemProxy section */
\t\t@@WIDGET_PROXY@@ /* PBXContainerItemProxy */ = {
\t\t\tisa = PBXContainerItemProxy;
\t\t\tcontainerPortal = @@PROJECT_ID@@ /* Project object */;
\t\t\tproxyType = 1;
\t\t\tremoteGlobalIDString = @@WIDGET_TARGET@@;
\t\t\tremoteInfo = @@WIDGET_NAME@@;
\t\t};
\t\t@@TEST_PROXY@@ /* PBXContainerItemProxy */ = {
\t\t\tisa = PBXContainerItemProxy;
\t\t\tcontainerPortal = @@PROJECT_ID@@ /* Project object */;
\t\t\tproxyType = 1;
\t\t\tremoteGlobalIDString = @@APP_TARGET@@;
\t\t\tremoteInfo = @@APP_NAME@@;
\t\t};
/* End PBXContainerItemProxy section */

/* Begin PBXCopyFilesBuildPhase section */
\t\t@@EMBED_PHASE@@ /* Embed App Extensions */ = {
\t\t\tisa = PBXCopyFilesBuildPhase;
\t\t\tbuildActionMask = 2147483647;
\t\t\tdstPath = "";
\t\t\tdstSubfolderSpec = 13;
\t\t\tfiles = (
\t\t\t\t@@EMBED_BUILD@@ /* @@WIDGET_NAME@@.appex in Embed App Extensions */,
\t\t\t);
\t\t\tname = "Embed App Extensions";
\t\t\trunOnlyForDeploymentPostprocessing = 0;
\t\t};
/* End PBXCopyFilesBuildPhase section */

/* Begin PBXFileReference section */
@@FILEREFS@@
/* End PBXFileReference section */

/* Begin PBXFrameworksBuildPhase section */
@@FRAMEWORKS@@
/* End PBXFrameworksBuildPhase section */

/* Begin PBXGroup section */
@@GROUPS@@
/* End PBXGroup section */

/* Begin PBXNativeTarget section */
@@NATIVETARGETS@@
/* End PBXNativeTarget section */

/* Begin PBXProject section */
@@PROJECT@@
/* End PBXProject section */

/* Begin PBXResourcesBuildPhase section */
@@RESOURCES@@
/* End PBXResourcesBuildPhase section */

/* Begin PBXSourcesBuildPhase section */
@@SOURCES@@
/* End PBXSourcesBuildPhase section */

/* Begin PBXTargetDependency section */
\t\t@@APP_DEP@@ /* PBXTargetDependency */ = {
\t\t\tisa = PBXTargetDependency;
\t\t\ttarget = @@WIDGET_TARGET@@ /* @@WIDGET_NAME@@ */;
\t\t\ttargetProxy = @@WIDGET_PROXY@@ /* PBXContainerItemProxy */;
\t\t};
\t\t@@TEST_DEP@@ /* PBXTargetDependency */ = {
\t\t\tisa = PBXTargetDependency;
\t\t\ttarget = @@APP_TARGET@@ /* @@APP_NAME@@ */;
\t\t\ttargetProxy = @@TEST_PROXY@@ /* PBXContainerItemProxy */;
\t\t};
/* End PBXTargetDependency section */

/* Begin XCBuildConfiguration section */
@@CONFIGURATIONS@@
/* End XCBuildConfiguration section */

/* Begin XCConfigurationList section */
@@CONFIGLISTS@@
/* End XCConfigurationList section */
\t};
\trootObject = @@PROJECT_ID@@ /* Project object */;
}
"""

pbxproj = (
    PBX_TEMPLATE.replace("@@BUILDFILES@@", "\n".join(build_file_lines))
    .replace("@@FILEREFS@@", "\n".join(file_ref_lines))
    .replace("@@WIDGET_PROXY@@", widget_proxy_id)
    .replace("@@TEST_PROXY@@", test_proxy_id)
    .replace("@@PROJECT_ID@@", project_id)
    .replace("@@WIDGET_TARGET@@", widget_target_id)
    .replace("@@WIDGET_NAME@@", WIDGET_NAME)
    .replace("@@APP_TARGET@@", target_id)
    .replace("@@APP_NAME@@", TARGET_NAME)
    .replace("@@EMBED_PHASE@@", embed_phase_id)
    .replace("@@EMBED_BUILD@@", embed_build_id)
    .replace("@@FRAMEWORKS@@", frameworks_section)
    .replace("@@GROUPS@@", "\n".join(group_lines))
    .replace("@@NATIVETARGETS@@", native_targets_section)
    .replace("@@PROJECT@@", project_section)
    .replace("@@RESOURCES@@", resources_section)
    .replace("@@SOURCES@@", sources_section)
    .replace("@@APP_DEP@@", app_dep_id)
    .replace("@@TEST_DEP@@", test_dep_id)
    .replace("@@CONFIGURATIONS@@", configurations_section)
    .replace("@@CONFIGLISTS@@", config_lists_section)
)

scheme = f"""<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="1500" version="1.7">
   <BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES">
      <BuildActionEntries>
         <BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES">
            <BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{target_id}" BuildableName="{TARGET_NAME}.app" BlueprintName="{TARGET_NAME}" ReferencedContainer="container:{TARGET_NAME}.xcodeproj"/>
         </BuildActionEntry>
         <BuildActionEntry buildForTesting="YES" buildForRunning="NO" buildForProfiling="NO" buildForArchiving="YES" buildForAnalyzing="YES">
            <BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{widget_target_id}" BuildableName="{WIDGET_NAME}.appex" BlueprintName="{WIDGET_NAME}" ReferencedContainer="container:{TARGET_NAME}.xcodeproj"/>
         </BuildActionEntry>
         <BuildActionEntry buildForTesting="YES" buildForRunning="NO" buildForProfiling="NO" buildForArchiving="NO" buildForAnalyzing="YES">
            <BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{test_target_id}" BuildableName="{TEST_NAME}.xctest" BlueprintName="{TEST_NAME}" ReferencedContainer="container:{TARGET_NAME}.xcodeproj"/>
         </BuildActionEntry>
      </BuildActionEntries>
   </BuildAction>
   <TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.DebuggerFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES">
      <Testables>
         <TestableReference skipped="NO">
            <BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{test_target_id}" BuildableName="{TEST_NAME}.xctest" BlueprintName="{TEST_NAME}" ReferencedContainer="container:{TARGET_NAME}.xcodeproj"/>
         </TestableReference>
      </Testables>
      <MacroExpansion>
         <BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{target_id}" BuildableName="{TARGET_NAME}.app" BlueprintName="{TARGET_NAME}" ReferencedContainer="container:{TARGET_NAME}.xcodeproj"/>
      </MacroExpansion>
   </TestAction>
   <LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.DebuggerFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES" debugServiceExtension="internal" allowLocationSimulation="YES">
      <BuildableProductRunnable runnableDebuggingMode="0">
         <BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{target_id}" BuildableName="{TARGET_NAME}.app" BlueprintName="{TARGET_NAME}" ReferencedContainer="container:{TARGET_NAME}.xcodeproj"/>
      </BuildableProductRunnable>
   </LaunchAction>
   <ProfileAction buildConfiguration="Release" shouldUseLaunchSchemeArgsEnv="YES" savedToolIdentifier="" useCustomWorkingDirectory="NO" debugDocumentVersioning="YES">
      <BuildableProductRunnable runnableDebuggingMode="0">
         <BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{target_id}" BuildableName="{TARGET_NAME}.app" BlueprintName="{TARGET_NAME}" ReferencedContainer="container:{TARGET_NAME}.xcodeproj"/>
      </BuildableProductRunnable>
   </ProfileAction>
   <AnalyzeAction buildConfiguration="Debug"/>
   <ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES"/>
</Scheme>
"""

workspace = """<?xml version="1.0" encoding="UTF-8"?>
<Workspace version="1.0">
   <FileRef location="self:">
   </FileRef>
</Workspace>
"""

# ---------------------------------------------------------------- write
(PROJECT).mkdir(parents=True, exist_ok=True)
(PROJECT / "project.pbxproj").write_text(pbxproj, encoding="utf-8")
schemes = PROJECT / "xcshareddata" / "xcschemes"
schemes.mkdir(parents=True, exist_ok=True)
(schemes / f"{SCHEME_NAME}.xcscheme").write_text(scheme, encoding="utf-8")
workspace_dir = PROJECT / "project.xcworkspace"
workspace_dir.mkdir(parents=True, exist_ok=True)
(workspace_dir / "contents.xcworkspacedata").write_text(workspace, encoding="utf-8")

# Leave no unreplaced tokens behind — a silent typo here would break Xcode.
leftover = [t for t in set(__import__("re").findall(r"@@[A-Z_]+@@", pbxproj))]
if leftover:
    raise SystemExit("Unreplaced pbxproj tokens: " + ", ".join(sorted(leftover)))

print("OK — wrote %s (%d targets, %d app sources, %d widget sources incl. %d shared, %d test sources, %d fonts, %d plist)" % (
    rel(PROJECT), 3, len(app_sources), len(widget_sources), len(shared_swift), len(test_sources),
    len(app_fonts), len(app_plists) + len(widget_plists)
))
