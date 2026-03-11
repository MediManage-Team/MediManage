# Diagram Export Pack

This folder contains presentation-ready exports generated from the editable sources in `docs/diagrams`.

## Recommended Use

- `a4-pdf/`
  Use these for print, sharing, or documentation bundles.
  Each diagram is placed on a single A4 landscape page.

- `a4-png/`
  Use these when you need page images instead of PDF pages.

- `ppt-png/`
  Use these for PowerPoint or slide decks.
  Each file is laid out on a 16:9 slide canvas.

- `svg/`
  Use these when you want crisp vector rendering and your presentation tool supports SVG import.

- `puml/`
  These are the PlantUML sources copied from `docs/diagrams` for traceability with the export pack.

## Bundle

- `a4-pdf/MediManage-Diagrams-A4-Pack.pdf`
  Combined multi-page A4 PDF containing the full diagram set.

## Regeneration

Run:

```powershell
python .\scripts\export_publish_diagrams.py
```

This will refresh the publish pack without changing the original editable diagram sources.
