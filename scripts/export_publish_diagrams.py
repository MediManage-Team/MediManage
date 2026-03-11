from __future__ import annotations

import shutil
import subprocess
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / "docs" / "diagrams"
OUTPUT_DIR = ROOT / "docs" / "diagrams-publish"
PLANTUML_JAR = Path(r"C:\Projects\plantuml.jar")
SOURCE_COMBINED_PDF = SOURCE_DIR / "Diagrams.pdf"

DIAGRAMS = [
    ("Module Diagram", "ModuleDiagram.puml"),
    ("Use Case Diagram", "UseCaseDiagram.puml"),
    ("Class Diagram", "ClassDiagram.puml"),
    ("Sequence Diagram", "SequenceDiagram.puml"),
    ("Collaboration Diagram", "CollaborationDiagram.puml"),
    ("Deployment Diagram", "DeploymentDiagram.puml"),
    ("System Architecture MVC", "SystemArchitectureMVC.puml"),
]

DIRS = {
    "puml": OUTPUT_DIR / "puml",
    "svg": OUTPUT_DIR / "svg",
    "png": OUTPUT_DIR / "png",
    "a4_png": OUTPUT_DIR / "a4-png",
    "a4_pdf": OUTPUT_DIR / "a4-pdf",
    "ppt_png": OUTPUT_DIR / "ppt-png",
}

A4_LANDSCAPE = (3508, 2480)
PPT_WIDE = (2560, 1440)

BG = (250, 251, 252)
PANEL = (255, 255, 255)
ACCENT = (20, 110, 180)
TEXT = (30, 41, 59)
MUTED = (92, 108, 128)
BORDER = (216, 222, 228)


def ensure_dirs() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    for directory in DIRS.values():
        directory.mkdir(parents=True, exist_ok=True)


def copy_sources() -> None:
    shutil.copy2(SOURCE_DIR / "Diagrams.md", OUTPUT_DIR / "Diagrams.md")
    for _, filename in DIAGRAMS:
        shutil.copy2(SOURCE_DIR / filename, DIRS["puml"] / filename)


def run_plantuml(fmt: str, out_dir: Path, source_file: Path) -> None:
    env = dict(**__import__("os").environ)
    env["PLANTUML_LIMIT_SIZE"] = "8192"
    command = [
        "java",
        "-jar",
        str(PLANTUML_JAR),
        f"--{fmt}",
        "--output-dir",
        str(out_dir),
        "--skinparam",
        "dpi=180",
        "--skinparam",
        "defaultFontName=Calibri",
        "--skinparam",
        "defaultFontSize=20",
        "--skinparam",
        "ArrowFontSize=18",
        "--skinparam",
        "Shadowing=false",
        str(source_file),
    ]
    subprocess.run(command, check=True, cwd=ROOT, env=env)


def render_base_assets() -> None:
    for _, filename in DIAGRAMS:
        source = SOURCE_DIR / filename
        run_plantuml("svg", DIRS["svg"], source)
        run_plantuml("png", DIRS["png"], source)


def load_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    candidates = [
        r"C:\Windows\Fonts\calibrib.ttf" if bold else r"C:\Windows\Fonts\calibri.ttf",
        r"C:\Windows\Fonts\arialbd.ttf" if bold else r"C:\Windows\Fonts\arial.ttf",
        r"C:\Windows\Fonts\segoeuib.ttf" if bold else r"C:\Windows\Fonts\segoeui.ttf",
    ]
    for candidate in candidates:
        path = Path(candidate)
        if path.exists():
            return ImageFont.truetype(str(path), size=size)
    return ImageFont.load_default()


def fit_diagram(diagram: Image.Image, max_w: int, max_h: int) -> Image.Image:
    scale = min(max_w / diagram.width, max_h / diagram.height)
    target = (
        max(1, int(diagram.width * scale)),
        max(1, int(diagram.height * scale)),
    )
    return diagram.resize(target, Image.LANCZOS)


def create_page_canvas(size: tuple[int, int], title: str, subtitle: str) -> tuple[Image.Image, tuple[int, int, int, int]]:
    width, height = size
    image = Image.new("RGB", size, BG)
    draw = ImageDraw.Draw(image)

    title_font = load_font(76 if width > 3000 else 42, bold=True)
    subtitle_font = load_font(30 if width > 3000 else 18)
    label_font = load_font(24 if width > 3000 else 14, bold=True)

    margin_x = 140 if width > 3000 else 72
    top_band_h = 180 if width > 3000 else 110
    footer_h = 70 if width > 3000 else 42

    draw.rectangle((0, 0, width, top_band_h), fill=PANEL)
    draw.rectangle((0, top_band_h - 16, width, top_band_h), fill=ACCENT)
    draw.text((margin_x, 34 if width > 3000 else 20), title, fill=TEXT, font=title_font)
    draw.text((margin_x, 108 if width > 3000 else 58), subtitle, fill=MUTED, font=subtitle_font)

    chip_text = "MediManage Documentation"
    chip_bbox = draw.textbbox((0, 0), chip_text, font=label_font)
    chip_w = chip_bbox[2] - chip_bbox[0] + 36
    chip_h = chip_bbox[3] - chip_bbox[1] + 18
    chip_x = width - margin_x - chip_w
    chip_y = 40 if width > 3000 else 24
    draw.rounded_rectangle((chip_x, chip_y, chip_x + chip_w, chip_y + chip_h), radius=18, fill=(237, 245, 251))
    draw.text((chip_x + 18, chip_y + 8), chip_text, fill=ACCENT, font=label_font)

    panel_left = margin_x
    panel_top = top_band_h + 48
    panel_right = width - margin_x
    panel_bottom = height - footer_h - 28
    draw.rounded_rectangle((panel_left, panel_top, panel_right, panel_bottom), radius=28, fill=PANEL, outline=BORDER, width=3)

    draw.text((margin_x, height - footer_h + 8), "Prepared for print and slide reuse", fill=MUTED, font=subtitle_font)

    content_box = (panel_left + 40, panel_top + 40, panel_right - 40, panel_bottom - 40)
    return image, content_box


def create_slide_canvas(size: tuple[int, int], title: str, subtitle: str) -> tuple[Image.Image, tuple[int, int, int, int]]:
    width, height = size
    image = Image.new("RGB", size, BG)
    draw = ImageDraw.Draw(image)

    title_font = load_font(58, bold=True)
    subtitle_font = load_font(24)
    label_font = load_font(18, bold=True)

    margin_x = 56
    top_band_h = 92
    footer_h = 28

    draw.rectangle((0, 0, width, top_band_h), fill=PANEL)
    draw.rectangle((0, top_band_h - 12, width, top_band_h), fill=ACCENT)
    draw.text((margin_x, 18), title, fill=TEXT, font=title_font)
    draw.text((margin_x, 62), subtitle, fill=MUTED, font=subtitle_font)

    chip_text = "MediManage Documentation"
    chip_bbox = draw.textbbox((0, 0), chip_text, font=label_font)
    chip_w = chip_bbox[2] - chip_bbox[0] + 30
    chip_h = chip_bbox[3] - chip_bbox[1] + 14
    chip_x = width - margin_x - chip_w
    chip_y = 20
    draw.rounded_rectangle((chip_x, chip_y, chip_x + chip_w, chip_y + chip_h), radius=16, fill=(237, 245, 251))
    draw.text((chip_x + 15, chip_y + 6), chip_text, fill=ACCENT, font=label_font)

    panel_left = 46
    panel_top = top_band_h + 20
    panel_right = width - 46
    panel_bottom = height - footer_h - 18
    draw.rounded_rectangle((panel_left, panel_top, panel_right, panel_bottom), radius=24, fill=PANEL, outline=BORDER, width=3)
    draw.text((56, height - footer_h + 2), "Prepared for print and slide reuse", fill=MUTED, font=subtitle_font)

    content_box = (panel_left + 18, panel_top + 18, panel_right - 18, panel_bottom - 18)
    return image, content_box


def place_diagram(base: Image.Image, diagram: Image.Image, content_box: tuple[int, int, int, int]) -> None:
    left, top, right, bottom = content_box
    fitted = fit_diagram(diagram, right - left, bottom - top)
    paste_x = left + ((right - left) - fitted.width) // 2
    paste_y = top + ((bottom - top) - fitted.height) // 2
    base.paste(fitted, (paste_x, paste_y))


def export_publish_assets() -> None:
    a4_pages: list[Image.Image] = []

    for title, filename in DIAGRAMS:
        stem = Path(filename).stem
        base_png = DIRS["png"] / f"{stem}.png"
        diagram = Image.open(base_png).convert("RGB")

        a4_page, a4_box = create_page_canvas(A4_LANDSCAPE, title, "A4 landscape - one diagram per page")
        place_diagram(a4_page, diagram, a4_box)
        a4_png_path = DIRS["a4_png"] / f"{stem}-A4.png"
        a4_pdf_path = DIRS["a4_pdf"] / f"{stem}-A4.pdf"
        a4_page.save(a4_png_path, quality=95)
        a4_page.save(a4_pdf_path, "PDF", resolution=300.0)
        a4_pages.append(a4_page.convert("RGB"))

        ppt_page, ppt_box = create_slide_canvas(PPT_WIDE, title, "16:9 slide-ready layout")
        place_diagram(ppt_page, diagram, ppt_box)
        ppt_page.save(DIRS["ppt_png"] / f"{stem}-PPT.png", quality=95)

    if a4_pages:
        first, rest = a4_pages[0], a4_pages[1:]
        first.save(
            DIRS["a4_pdf"] / "MediManage-Diagrams-A4-Pack.pdf",
            "PDF",
            resolution=300.0,
            save_all=True,
            append_images=rest,
        )


def mirror_source_pdfs() -> None:
    combined_pack = DIRS["a4_pdf"] / "MediManage-Diagrams-A4-Pack.pdf"
    shutil.copy2(combined_pack, SOURCE_COMBINED_PDF)

    for _, filename in DIAGRAMS:
        stem = Path(filename).stem
        shutil.copy2(DIRS["a4_pdf"] / f"{stem}-A4.pdf", SOURCE_DIR / f"{stem}.pdf")


def main() -> None:
    if not PLANTUML_JAR.exists():
        raise FileNotFoundError(f"PlantUML jar not found: {PLANTUML_JAR}")
    ensure_dirs()
    copy_sources()
    render_base_assets()
    export_publish_assets()
    mirror_source_pdfs()


if __name__ == "__main__":
    main()
