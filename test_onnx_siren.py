import sys
import torch
import torch.nn as nn
import onnx
import onnxruntime as ort
import numpy as np

print("Imports done", flush=True)

class SirenClassifierNet(nn.Module):
    def __init__(self, num_classes=3):
        super().__init__()
        self.conv1 = nn.Conv1d(1, 32, kernel_size=80, stride=16)
        self.bn1 = nn.BatchNorm1d(32)
        self.relu = nn.ReLU()
        self.pool1 = nn.MaxPool1d(4)
        
        self.conv2 = nn.Conv1d(32, 64, kernel_size=3, padding=1)
        self.bn2 = nn.BatchNorm1d(64)
        self.pool2 = nn.MaxPool1d(4)

        self.conv3 = nn.Conv1d(64, 64, kernel_size=3, padding=1)
        self.bn3 = nn.BatchNorm1d(64)
        
        self.fc = nn.Linear(64, num_classes)

    def forward(self, x):
        if x.dim() == 2:
            x = x.unsqueeze(1)
        x = self.pool1(self.relu(self.bn1(self.conv1(x))))
        x = self.pool2(self.relu(self.bn2(self.conv2(x))))
        x = self.relu(self.bn3(self.conv3(x)))
        x = torch.mean(x, dim=-1)
        return self.fc(x)

net = SirenClassifierNet(num_classes=3)
net.eval()
dummy = torch.randn(1, 16000)
out_pt = net(dummy).detach().numpy()
print("Forward done:", out_pt, flush=True)

torch.onnx.export(
    net, dummy, "test_export.onnx",
    input_names=["audio_input"],
    output_names=["logits"],
    opset_version=14,
    dynamo=False
)
print("Export done", flush=True)

m = onnx.load("test_export.onnx")
m.ir_version = 8
onnx.checker.check_model(m)
onnx.save(m, "test_export.onnx")

sess = ort.InferenceSession("test_export.onnx")
out_onnx = sess.run(None, {"audio_input": dummy.numpy()})[0]
print("ONNX done:", out_onnx, flush=True)
print("Difference:", np.max(np.abs(out_pt - out_onnx)), flush=True)
