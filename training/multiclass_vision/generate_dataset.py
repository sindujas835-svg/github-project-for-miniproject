"""
Multi-Class Traffic & Perception Dataset Generator
Generates clean, diverse samples for:
  Class 0: ambulance
  Class 1: person
  Class 2: car
  Class 3: bus
  Class 4: truck
  Class 5: motorcycle
  Negative: empty background / road / room
"""

import os
import random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

random.seed(42)
np.random.seed(42)

def draw_road_background(width=640, height=640, bg_type="road"):
    img = Image.new("RGB", (width, height))
    draw = ImageDraw.Draw(img)
    if bg_type == "road":
        # Sky
        draw.rectangle([0, 0, width, 220], fill=(130, 175, 220))
        # Asphalt road
        draw.rectangle([0, 220, width, height], fill=(50, 53, 58))
        # Perspective lanes
        draw.line([320, 220, 100, height], fill=(230, 230, 210), width=3)
        draw.line([320, 220, 540, height], fill=(230, 230, 210), width=3)
        for y in range(230, height, 40):
            draw.line([320, y, 320, min(y + 25, height)], fill=(220, 180, 40), width=4)
    elif bg_type == "indoor":
        # Wall
        draw.rectangle([0, 0, width, 400], fill=(210, 215, 220))
        # Floor / desk
        draw.rectangle([0, 400, width, height], fill=(140, 110, 85))
        # Window frame
        draw.rectangle([50, 50, 200, 250], outline=(80, 80, 90), width=4, fill=(160, 200, 240))
    else: # night road
        draw.rectangle([0, 0, width, 220], fill=(10, 12, 20))
        draw.rectangle([0, 220, width, height], fill=(25, 27, 30))
        draw.line([320, 220, 100, height], fill=(150, 150, 100), width=2)
        draw.line([320, 220, 540, height], fill=(150, 150, 100), width=2)
    return img

def render_ambulance(img):
    draw = ImageDraw.Draw(img)
    w, h = random.randint(180, 240), random.randint(150, 200)
    x1 = random.randint(50, 640 - w - 50)
    y1 = random.randint(240, 640 - h - 30)
    x2, y2 = x1 + w, y1 + h

    # White box body
    draw.rectangle([x1, y1 + 25, x2, y2], fill=(245, 248, 252), outline=(40, 40, 40), width=3)
    # Windshield
    draw.rectangle([x1 + 10, y1 + 35, x2 - 10, y1 + 80], fill=(40, 55, 75))
    # Red stripe
    stripe_y1 = y1 + int(h * 0.55)
    draw.rectangle([x1, stripe_y1, x2, stripe_y1 + 25], fill=(220, 25, 25))
    # Red Cross Emblem
    cx, cy = (x1 + x2) // 2, y1 + int(h * 0.78)
    arm = 10
    draw.rectangle([cx - arm, cy - 25, cx + arm, cy + 25], fill=(220, 25, 25))
    draw.rectangle([cx - 25, cy - arm, cx + 25, cy + arm], fill=(220, 25, 25))
    # Emergency Roof Lightbar (Red + Blue)
    draw.rectangle([cx - 40, y1 + 10, cx - 2, y1 + 25], fill=(255, 30, 30))
    draw.rectangle([cx + 2, y1 + 10, cx + 40, y1 + 25], fill=(30, 80, 255))
    # Wheels
    draw.ellipse([x1 + 20, y2 - 20, x1 + 60, y2 + 15], fill=(20, 20, 20))
    draw.ellipse([x2 - 60, y2 - 20, x2 - 20, y2 + 15], fill=(20, 20, 20))

    return [0, (x1 + x2) / (2 * 640.0), (y1 + y2) / (2 * 640.0), w / 640.0, h / 640.0]

def render_person(img):
    draw = ImageDraw.Draw(img)
    w, h = random.randint(60, 100), random.randint(180, 320)
    x1 = random.randint(50, 640 - w - 50)
    y1 = random.randint(220, 640 - h - 20)
    x2, y2 = x1 + w, y1 + h

    cx = (x1 + x2) // 2
    head_r = int(w * 0.28)
    head_cy = y1 + head_r + 5

    # Head (skin tone)
    skin_colors = [(240, 200, 170), (210, 160, 120), (140, 95, 60), (250, 220, 190)]
    skin = random.choice(skin_colors)
    draw.ellipse([cx - head_r, head_cy - head_r, cx + head_r, head_cy + head_r], fill=skin, outline=(50, 30, 20), width=2)

    # Hair
    draw.arc([cx - head_r, head_cy - head_r - 2, cx + head_r, head_cy], 180, 360, fill=(30, 20, 15), width=6)

    # Torso (colored shirt/jacket)
    shirt_colors = [(50, 90, 160), (180, 50, 50), (40, 140, 60), (40, 40, 40), (220, 180, 40)]
    shirt = random.choice(shirt_colors)
    torso_top = head_cy + head_r + 2
    torso_h = int(h * 0.40)
    draw.rectangle([cx - int(w * 0.45), torso_top, cx + int(w * 0.45), torso_top + torso_h], fill=shirt, outline=(30, 30, 30), width=2)

    # Legs (pants)
    pant_colors = [(30, 40, 60), (50, 50, 50), (120, 110, 100)]
    pants = random.choice(pant_colors)
    pant_top = torso_top + torso_h
    leg_w = int(w * 0.18)
    draw.rectangle([cx - int(w * 0.40), pant_top, cx - 2, y2], fill=pants)
    draw.rectangle([cx + 2, pant_top, cx + int(w * 0.40), y2], fill=pants)

    # NO lightbars, NO red cross, NO vehicle box!
    return [1, (x1 + x2) / (2 * 640.0), (y1 + y2) / (2 * 640.0), w / 640.0, h / 640.0]

def render_car(img):
    draw = ImageDraw.Draw(img)
    w, h = random.randint(160, 220), random.randint(110, 150)
    x1 = random.randint(50, 640 - w - 50)
    y1 = random.randint(260, 640 - h - 30)
    x2, y2 = x1 + w, y1 + h

    car_colors = [(30, 90, 180), (180, 30, 30), (50, 50, 55), (190, 195, 200), (210, 170, 30)]
    body_color = random.choice(car_colors)

    # Car lower chassis
    draw.rectangle([x1, y1 + int(h * 0.40), x2, y2], fill=body_color, outline=(30, 30, 30), width=2)
    # Car roof / cabin
    roof_w = int(w * 0.60)
    roof_x1 = x1 + int(w * 0.20)
    draw.rectangle([roof_x1, y1, roof_x1 + roof_w, y1 + int(h * 0.45)], fill=body_color, outline=(30, 30, 30), width=2)
    # Windshield & windows
    draw.rectangle([roof_x1 + 6, y1 + 6, roof_x1 + roof_w - 6, y1 + int(h * 0.40)], fill=(40, 55, 75))
    # Headlights
    draw.rectangle([x1 + 4, y1 + int(h * 0.50), x1 + 18, y1 + int(h * 0.70)], fill=(255, 255, 200))
    draw.rectangle([x2 - 18, y1 + int(h * 0.50), x2 - 4, y1 + int(h * 0.70)], fill=(255, 255, 200))
    # Wheels
    draw.ellipse([x1 + 25, y2 - 18, x1 + 60, y2 + 12], fill=(20, 20, 20))
    draw.ellipse([x2 - 60, y2 - 18, x2 - 25, y2 + 12], fill=(20, 20, 20))

    return [2, (x1 + x2) / (2 * 640.0), (y1 + y2) / (2 * 640.0), w / 640.0, h / 640.0]

def render_bus(img):
    draw = ImageDraw.Draw(img)
    w, h = random.randint(240, 320), random.randint(180, 240)
    x1 = random.randint(20, 640 - w - 20)
    y1 = random.randint(230, 640 - h - 30)
    x2, y2 = x1 + w, y1 + h

    bus_color = (235, 180, 30) # Yellow school/transit bus or Blue transit
    if random.random() > 0.5:
        bus_color = (30, 110, 190)

    draw.rectangle([x1, y1, x2, y2], fill=bus_color, outline=(20, 20, 20), width=3)
    # Windows row
    win_y = y1 + 30
    for wx in range(x1 + 15, x2 - 35, 45):
        draw.rectangle([wx, win_y, wx + 35, win_y + 40], fill=(40, 60, 80))
    # Destination board
    draw.rectangle([x1 + 40, y1 + 6, x2 - 40, y1 + 22], fill=(10, 10, 10))
    # Wheels
    draw.ellipse([x1 + 40, y2 - 25, x1 + 80, y2 + 15], fill=(20, 20, 20))
    draw.ellipse([x2 - 80, y2 - 25, x2 - 40, y2 + 15], fill=(20, 20, 20))

    return [3, (x1 + x2) / (2 * 640.0), (y1 + y2) / (2 * 640.0), w / 640.0, h / 640.0]

def render_truck(img):
    draw = ImageDraw.Draw(img)
    w, h = random.randint(220, 300), random.randint(160, 220)
    x1 = random.randint(30, 640 - w - 30)
    y1 = random.randint(230, 640 - h - 30)
    x2, y2 = x1 + w, y1 + h

    # Large cargo box (metallic gray)
    box_w = int(w * 0.70)
    draw.rectangle([x1, y1, x1 + box_w, y2 - 10], fill=(180, 185, 195), outline=(50, 50, 50), width=2)
    # Cab
    cab_x1 = x1 + box_w
    draw.rectangle([cab_x1, y1 + 40, x2, y2], fill=(190, 50, 40), outline=(50, 50, 50), width=2)
    draw.rectangle([cab_x1 + 10, y1 + 50, x2 - 10, y1 + 90], fill=(40, 55, 75))
    # Wheels
    draw.ellipse([x1 + 30, y2 - 22, x1 + 65, y2 + 15], fill=(20, 20, 20))
    draw.ellipse([x1 + 75, y2 - 22, x1 + 110, y2 + 15], fill=(20, 20, 20))
    draw.ellipse([x2 - 50, y2 - 22, x2 - 15, y2 + 15], fill=(20, 20, 20))

    return [4, (x1 + x2) / (2 * 640.0), (y1 + y2) / (2 * 640.0), w / 640.0, h / 640.0]

def render_motorcycle(img):
    draw = ImageDraw.Draw(img)
    w, h = random.randint(90, 140), random.randint(110, 160)
    x1 = random.randint(50, 640 - w - 50)
    y1 = random.randint(260, 640 - h - 30)
    x2, y2 = x1 + w, y1 + h

    cx = (x1 + x2) // 2
    # Wheels
    draw.ellipse([x1 + 5, y2 - 40, x1 + 45, y2], fill=(20, 20, 20), outline=(80, 80, 80), width=4)
    draw.ellipse([x2 - 45, y2 - 40, x2 - 5, y2], fill=(20, 20, 20), outline=(80, 80, 80), width=4)
    # Frame / Engine
    draw.polygon([(x1 + 25, y2 - 20), (cx, y1 + 40), (x2 - 25, y2 - 20)], fill=(160, 40, 40), outline=(30, 30, 30))
    # Rider
    draw.ellipse([cx - 15, y1, cx + 15, y1 + 30], fill=(40, 40, 40)) # Helmet
    draw.rectangle([cx - 18, y1 + 30, cx + 18, y1 + 70], fill=(30, 60, 100)) # Jacket

    return [5, (x1 + x2) / (2 * 640.0), (y1 + y2) / (2 * 640.0), w / 640.0, h / 640.0]

def build_dataset(base_dir):
    splits = {"train": 180, "val": 40, "test": 40} # per class
    classes = [
        ("ambulance", render_ambulance),
        ("person", render_person),
        ("car", render_car),
        ("bus", render_bus),
        ("truck", render_truck),
        ("motorcycle", render_motorcycle),
        ("background", None)
    ]

    for split, count in splits.items():
        img_dir = os.path.join(base_dir, split, "images")
        lbl_dir = os.path.join(base_dir, split, "labels")
        os.makedirs(img_dir, exist_ok=True)
        os.makedirs(lbl_dir, exist_ok=True)

        idx = 0
        for cls_name, render_fn in classes:
            samples_for_cls = count if cls_name != "background" else count // 2
            for s in range(samples_for_cls):
                bg_type = "indoor" if (cls_name == "person" and random.random() > 0.4) else ("night" if random.random() > 0.8 else "road")
                img = draw_road_background(640, 640, bg_type)
                
                label_line = ""
                if render_fn is not None:
                    c, cx, cy, w, h = render_fn(img)
                    label_line = f"{c} {cx:.6f} {cy:.6f} {w:.6f} {h:.6f}"

                # Noise & slight blur for robustness
                if random.random() > 0.5:
                    img = img.filter(ImageFilter.GaussianBlur(radius=0.5))

                fname = f"{cls_name}_{split}_{idx:04d}"
                img_path = os.path.join(img_dir, fname + ".jpg")
                lbl_path = os.path.join(lbl_dir, fname + ".txt")

                img.save(img_path, quality=90)
                with open(lbl_path, "w") as f:
                    if label_line:
                        f.write(label_line + "\n")

                idx += 1
        print(f"Generated {idx} images in split '{split}'")

if __name__ == "__main__":
    out_dir = r"c:\Users\shri\Downloads\traffic-routing-prototype\training\multiclass_vision\dataset"
    print("Generating Multi-Class Vision Dataset...")
    build_dataset(out_dir)
    print("Dataset generation completed!")

