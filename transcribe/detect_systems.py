"""Locate staff systems on each sheet-music page so they can be cropped at high zoom.

A staff line is a long horizontal run of dark pixels, so rows belonging to a staff
have far more dark pixels than rows of white space or text.
"""
import json
import os
import sys

from PIL import Image

ATTACH = r"c:\Users\xuzim\.trae-cn\attachments\6ac49131bef521b729190846"

PAGES = [
    ("p1", "d6d6f197-9ac5-4425-b9c6-99c5ed4197d1_164459ca-5d0b-44c7-8193-8c36d81c9597_5.JPG"),
    ("p2", "333b4e09-b435-4517-8d16-d42202f9ad95_a20ba187-13d1-48f8-b2d9-e2cf377bed0b_6.JPG"),
    ("p3", "fccc663b-a5a7-4553-a6d5-a76854fb8087_5437d611-92c4-4dfd-8233-4b16ca050ebb_7.JPG"),
    ("p4", "e6713446-9ab4-42eb-8d64-5d2f9e500857_673474e0-eba9-4f76-aa3c-12a424ecedb5_8.JPG"),
    ("p5", "0fd6a61e-c656-4c75-bd6e-3f3bb68f96ac_a9fbb87e-51c5-4e93-9524-8704f35b2b77_9.JPG"),
    ("p6", "9e324f9b-dd1c-41d3-97ea-b4e3c331d62a_2461dafc-166b-4cef-9257-f0386349fcbf_10.JPG"),
]


def dark_row_profile(image):
    """Count of dark pixels per row, downsampled horizontally for speed."""
    gray = image.convert("L")
    width, height = gray.size
    # Sample every 2nd column: staff lines are unbroken, so this is plenty.
    pixels = gray.load()
    profile = [0] * height
    for y in range(height):
        count = 0
        for x in range(0, width, 2):
            if pixels[x, y] < 128:
                count += 1
        profile[y] = count
    return profile


def find_staff_bands(profile, width, min_fraction=0.35):
    """Return (top, bottom) for each group of consecutive staff-line rows."""
    threshold = width / 2 * min_fraction
    bands = []
    start = None
    for y, count in enumerate(profile):
        if count >= threshold:
            if start is None:
                start = y
        else:
            if start is not None:
                bands.append((start, y - 1))
                start = None
    if start is not None:
        bands.append((start, len(profile) - 1))
    return bands


def group_into_systems(bands, max_gap=100):
    """Pair the five-line staffs of the grand staff into systems.

    Within one grand staff the treble and bass are ~60px apart; between two
    consecutive systems the gap is ~150px, so 100 separates them cleanly.
    """
    if not bands:
        return []
    systems = []
    current = [bands[0]]
    for band in bands[1:]:
        if band[0] - current[-1][1] <= max_gap:
            current.append(band)
        else:
            systems.append(current)
            current = [band]
    systems.append(current)

    result = []
    for group in systems:
        top = group[0][0]
        bottom = group[-1][1]
        result.append({"top": top, "bottom": bottom, "staffs": group})
    return result


def main():
    out = {}
    for name, filename in PAGES:
        path = os.path.join(ATTACH, filename)
        image = Image.open(path)
        width, _ = image.size
        profile = dark_row_profile(image)
        bands = find_staff_bands(profile, width)
        systems = group_into_systems(bands)
        out[name] = {"size": image.size, "systems": systems}
        print(f"{name}: {image.size} -> {len(systems)} systems")
        for index, system in enumerate(systems):
            print(f"   system {index}: y {system['top']}..{system['bottom']} "
                  f"({len(system['staffs'])} staffs)")

    with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "systems.json"), "w") as handle:
        json.dump(out, handle, indent=2)


if __name__ == "__main__":
    sys.exit(main())
