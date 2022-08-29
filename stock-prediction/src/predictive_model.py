import torch
import pathlib
import os

MODEL_DIR = os.path.dirname(os.path.realpath(__file__)) + "/../models/"

class PredictiveModel():
    def __init__(self, model, name, window, created_date, device="cpu"):
        self.id = f"{name}|{window}|{created_date}"
        self.model = model
        self.name = name
        self.window = window
        self.device = device

    def predict(self, X):
        # PyTorch Model
        if isinstance(self.model, torch.nn.Module):
            return self.model(torch.from_numpy(X).float().to(self.device)).detach().numpy()
        # Other callable model (TF?)
        elif callable(self.model):
            return self.model(X)
        # Scikit-learn
        else:
            try:
                return self.model.predict(X)
            except:
                pass
        raise "Invalid Model!"

    def __call__(self, X):
        return self.predict(X)

    def save(self):
        if isinstance(self.model, torch.nn.Module):
            pathlib.Path(MODEL_DIR).mkdir(parents=True, exist_ok=True)
            torch.save(self.model.state_dict(), MODEL_DIR + self.id + ".pt")
        else:
            raise "Can't save this model! Not yet implemented."

    @staticmethod
    def load(state_dict_file, model):
        name, window, created_date = os.path.splitext(state_dict_file)[0].split('|')
        model.load_state_dict(torch.load(MODEL_DIR + "/" + state_dict_file))
        model.eval()
        return PredictiveModel(model, name, window, created_date)
