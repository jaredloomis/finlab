import { makeAutoObservable } from "mobx";

class ModelConfig {
  modelName?: string;

  constructor() {
    makeAutoObservable(this);
  }

  setModelName(modelName: string) {
    this.modelName = modelName;
  }
}
