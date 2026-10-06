"""Crop every grand-staff system at high zoom so the notation is readable."""
import json
import os

from PIL import Image

ATTACH = r"c:\Users\xuzim\.trae-cn\attachments\6ac49131bef521b729190846"
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "crops")

PAGES = {
    "p1": "d6d6f197-9ac5-4425-b9c6-99c5ed4197d1_164459ca-5d0b-44c7-8193-8c36d81c9597_5.JPG",
    "p2": "333b4e09-b435-4517-8d16-d42202f9ad95_a20ba187-13d1-48f8-b2d9-e2cf377bed0b_6.JPG",
    "p3": "fccc663b-a5a7-4553-a6d5-a76854fb8087_5437d611-92c4-4dfd-8233-4b16ca050ebb_7.JPG",
    "p4": "e6713446-9ab4-42eb-8d64-5d2f9e500857_673474e0-eba9-4f76-aa3c-12a424ecedb5_8.JPG",
    "p5": "0fd6a61e-c656-4c75-bd6e-3f3bb68f96ac_a9fbb87e-51c5-4e93-9524-8704f35b2b77_9.JPG",
    "p6": "9e324f9b-dd1c-41d3-97ea-b4e3c331d62a_2461dafc-166b-4cef-9257-f0386349fcbf_10.JPG",
}

# Measures each page starts at, and how many systems it holds.
PAGE_FIRST_MEASURE = {"p1": 1, "p2": 17, "p3": 37, "p4": 57, "p5": 77, "p6": 97}

PAD_TOP = 26
PAD_BOTTOM = 26
SCALE = 2


def main():
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(HERE, "systems.json")) as handle:
        detected = json.load(handle)

    manifest = []
    for page, filename in PAGES.items():
        image = Image.open(os.path.join(ATTACH, filename))
        width, height = image.size
        systems = detected[page]["systems"]
        first = PAGE_FIRST_MEASURE[page]

        for index, system in enumerate(systems):
            top = max(0, system["top"] - PAD_TOP)
            bottom = min(height, system["bottom"] + PAD_BOTTOM)
            crop = image.crop((0, top, width, bottom))
            crop = crop.resize((width * SCALE, (bottom - top) * SCALE), Image.LANCZOS)

            measure_from = first + index * 4
            name = f"{page}_s{index}_m{measure_from:03d}.png"
            crop.save(os.path.join(OUT, name))
            manifest.append({
                "file": name,
                "page": page,
                "system": index,
                "measureFrom": measure_from,
                "measureTo": measure_from + 3,
            })
            print(f"{name}  y {top}..{bottom}  measures {measure_from}-{measure_from + 3}")

    with open(os.path.join(HERE, "crops.json"), "w") as handle:
        json.dump(manifest, handle, indent=2)
    print(f"\n{len(manifest)} crops written to {OUT}")


if __name__ == "__main__":
    main()