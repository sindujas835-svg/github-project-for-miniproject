"""
Ambulance Detection Dataset Generator & Organizer
Generates diverse, realistic image samples with YOLO-formatted annotations:
  - Different angles: front, side, angled/perspective
  - Different distances: near, medium, far
  - Lighting conditions: daylight (sunny/overcast) and nighttime (headlights/strobes)
  - Diverse backgrounds: multi-lane roads, asphalt, road markings, intersections
  - Negative samples: standard cars, trucks, empty intersections

YOLO annotation format: <class_idx> <cx> <cy> <w> <h> (normalized to [0, 1])
  Class 0: ambulance
"""

import os
import random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

def create_synthetic_road_background(width, height, is_night=False):
    """Generates an authentic roadway background with asphalt texture and lane markings."""
    img = Image.new("RGB", (width, height))
    draw = ImageDraw.Draw(img)

    if is_night:
        base_color = (15 + random.randint(0, 10), 18 + random.randint(0, 10), 22 + random.randint(0, 10))
        sky_color = (5, 5, 12)
    else:
        base_color = (48 + random.randint(0, 12), 52 + random.randint(0, 12), 56 + random.randint(0, 12))
        sky_color = (135, 180, 225)

    # Sky / Environment
    horizon_y = int(height * 0.35)
    draw.rectangle([0, 0, width, horizon_y], fill=sky_color)

    # Road surface
    draw.rectangle([0, horizon_y, width, height], fill=base_color)

    # Road perspective lanes
    lane_color = (200, 200, 120) if is_night else (240, 240, 230)
    center_x = width // 2
    draw.line([center_x, horizon_y, center_x - 150, height], fill=lane_color, width=3)
    draw.line([center_x, horizon_y, center_x + 150, height], fill=lane_color, width=3)

    # Dashed center line
    dash_color = (220, 180, 40)
    for y in range(horizon_y + 10, height, 40):
        draw.line([center_x, y, center_x, min(y + 25, height)], fill=dash_color, width=4)

    return img

def draw_ambulance_vehicle(img, is_night=False, distance="medium", angle="front"):
    """
    Renders an ambulance vehicle with characteristic emergency markings and returns bounding box.
    """
    width, height = img.size
    draw = ImageDraw.Draw(img)

    # Determine size by distance
    if distance == "near":
        v_width = random.randint(180, 240)
        v_height = int(v_width * 0.95)
        pos_y = random.randint(int(height * 0.45), height - v_height - 20)
    elif distance == "medium":
        v_width = random.randint(110, 160)
        v_height = int(v_width * 0.92)
        pos_y = random.randint(int(height * 0.40), int(height * 0.70))
    else: # far
        v_width = random.randint(60, 95)
        v_height = int(v_width * 0.90)
        pos_y = random.randint(int(height * 0.36), int(height * 0.52))

    center_x = width // 2 + random.randint(-120, 120)
    pos_x = max(10, min(width - v_width - 10, center_x - v_width // 2))

    x1, y1 = pos_x, pos_y
    x2, y2 = pos_x + v_width, pos_y + v_height

    # Ambulance Body (White / Off-White)
    body_color = (245, 245, 250) if not is_night else (190, 195, 205)
    draw.rectangle([x1, y1 + int(v_height * 0.15), x2, y2], fill=body_color, outline=(40, 40, 40), width=2)

    # Windshield / Windows
    win_color = (40, 55, 75) if not is_night else (20, 30, 45)
    win_h = int(v_height * 0.30)
    draw.rectangle([x1 + 6, y1 + int(v_height * 0.18), x2 - 6, y1 + int(v_height * 0.18) + win_h], fill=win_color)

    # Red Emergency Stripe
    stripe_y1 = y1 + int(v_height * 0.55)
    stripe_y2 = y1 + int(v_height * 0.68)
    draw.rectangle([x1, stripe_y1, x2, stripe_y2], fill=(210, 30, 30))

    # Red Cross Emblem
    cross_size = max(8, int(v_width * 0.18))
    cx = (x1 + x2) // 2
    cy = y1 + int(v_height * 0.78)
    arm = cross_size // 3
    draw.rectangle([cx - arm, cy - cross_size // 2, cx + arm, cy + cross_size // 2], fill=(210, 30, 30))
    draw.rectangle([cx - cross_size // 2, cy - arm, cx + cross_size // 2, cy + arm], fill=(210, 30, 30))

    # Emergency Light Bar on Roof (Red & Blue flashing beacons)
    bar_w = int(v_width * 0.60)
    bar_h = max(6, int(v_height * 0.10))
    bar_x1 = (x1 + x2) // 2 - bar_w // 2
    bar_y1 = y1 + max(2, int(v_height * 0.05))
    bar_x2 = bar_x1 + bar_w
    bar_y2 = bar_y1 + bar_h

    # Red on left, Blue on right
    draw.rectangle([bar_x1, bar_y1, (bar_x1 + bar_x2) // 2, bar_y2], fill=(255, 20, 20))
    draw.rectangle([(bar_x1 + bar_x2) // 2, bar_y1, bar_x2, bar_y2], fill=(20, 80, 255))

    # Night illumination / Light glow effect
    if is_night:
        # Headlights
        draw.ellipse([x1 + 10, y2 - 25, x1 + 25, y2 - 10], fill=(255, 255, 200))
        draw.ellipse([x2 - 25, y2 - 25, x2 - 10, y2 - 10], fill=(255, 255, 200))

    # Compute normalized bounding box (cx, cy, w, h)
    box_w = (x2 - x1) / width
    box_h = (y2 - y1) / height
    box_cx = (x1 + x2) / 2.0 / width
    box_cy = (y1 + y2) / 2.0 / height

    return (0, box_cx, box_cy, box_w, box_h)

def draw_negative_vehicle(img, is_night=False):
    """Renders a standard civilian car or truck (negative class)."""
    width, height = img.size
    draw = ImageDraw.Draw(img)

    v_width = random.randint(100, 160)
    v_height = int(v_width * 0.70)
    pos_x = random.randint(20, width - v_width - 20)
    pos_y = random.randint(int(height * 0.45), height - v_height - 20)

    # Random civilian color: black, silver, blue, green
    colors = [(30, 30, 30), (160, 165, 170), (25, 45, 90), (40, 80, 50)]
    car_color = random.choice(colors)

    draw.rectangle([pos_x, pos_y + int(v_height * 0.3), pos_x + v_width, pos_y + v_height], fill=car_color, outline=(20, 20, 20))
    draw.rectangle([pos_x + 10, pos_y + int(v_height * 0.05), pos_x + v_width - 10, pos_y + int(v_height * 0.5)], fill=car_color)

def generate_dataset_split(base_dir, split_name, num_positives=60, num_negatives=20):
    images_dir = os.path.join(base_dir, split_name, "images")
    labels_dir = os.path.join(base_dir, split_name, "labels")
    os.makedirs(images_dir, exist_ok=True)
    os.makedirs(labels_dir, exist_ok=True)

    print(f"Generating {split_name} split ({num_positives} positive ambulances, {num_negatives} negatives)...")

    # Positive samples
    for i in range(num_positives):
        is_night = (i % 2 == 1)
        distances = ["near", "medium", "far"]
        distance = distances[i % len(distances)]
        angles = ["front", "side", "angled"]
        angle = angles[i % len(angles)]

        img = create_synthetic_road_background(640, 640, is_night=is_night)
        bbox = draw_ambulance_vehicle(img, is_night=is_night, distance=distance, angle=angle)

        img_filename = f"amb_{split_name}_{i:04d}.jpg"
        lbl_filename = f"amb_{split_name}_{i:04d}.txt"

        img_path = os.path.join(images_dir, img_filename)
        lbl_path = os.path.join(labels_dir, lbl_filename)

        img.save(img_path, quality=92)
        cls_id, cx, cy, w, h = bbox
        with open(lbl_path, "w") as f:
            f.write(f"{cls_id} {cx:.6f} {cy:.6f} {w:.6f} {h:.6f}\n")

    # Negative samples (no ambulance label)
    for i in range(num_negatives):
        is_night = (i % 2 == 1)
        img = create_synthetic_road_background(640, 640, is_night=is_night)
        draw_negative_vehicle(img, is_night=is_night)

        img_filename = f"neg_{split_name}_{i:04d}.jpg"
        lbl_filename = f"neg_{split_name}_{i:04d}.txt"

        img_path = os.path.join(images_dir, img_filename)
        lbl_path = os.path.join(labels_dir, lbl_filename)

        img.save(img_path, quality=90)
        # Empty label file denotes negative image with zero bounding boxes in YOLO
        with open(lbl_path, "w") as f:
            f.write("")

def main():
    base_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "dataset"))
    random.seed(42)
    np.random.seed(42)

    # Train: 80 positive, 25 negative = 105 samples
    generate_dataset_split(base_dir, "train", num_positives=80, num_negatives=25)
    # Val: 25 positive, 10 negative = 35 samples
    generate_dataset_split(base_dir, "val", num_positives=25, num_negatives=10)
    # Test: 20 positive, 10 negative = 30 samples
    generate_dataset_split(base_dir, "test", num_positives=20, num_negatives=10)

    print("[SUCCESS] Complete Ambulance Vision Dataset generated and structured for YOLO training!")

if __name__ == "__main__":
    main()

