import onnx
import onnxruntime as ort
import os

model_paths = [
    r"c:\Users\shri\Downloads\traffic-routing-prototype\models\camera\ambulance_yolov8_best.onnx",
    r"c:\Users\shri\Downloads\traffic-routing-prototype\traffic-backend\traffic-backend\models\vision\yolov8n_ambulance.onnx"
]

for p in model_paths:
    print("=" * 60)
    print("Path:", p)
    if not os.path.exists(p):
        print("DOES NOT EXIST!")
        continue
    m = onnx.load(p)
    print("IR Version:", m.ir_version)
    for i in m.graph.input:
        shape = [d.dim_value if d.dim_value != 0 else d.dim_param for d in i.type.tensor_type.shape.dim]
        print(f"Input: {i.name}, shape={shape}")
    for o in m.graph.output:
        shape = [d.dim_value if d.dim_value != 0 else d.dim_param for d in o.type.tensor_type.shape.dim]
        print(f"Output: {o.name}, shape={shape}")
    print("Metadata props:")
    for prop in m.metadata_props:
        print(f"  {prop.key}: {prop.value}")

