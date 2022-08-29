import { makeAutoObservable } from "mobx";

class ModelConfig {
  modelId?: string;

  constructor() {
    makeAutoObservable(this);
  }

  setModelId = (modelId: string) => {
    this.modelId = modelId;
  }
}

export default ModelConfig;
