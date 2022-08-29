import { observable } from "mobx";
import { observer } from "mobx-react-lite";
import { Routes, Route, Link } from "react-router-dom";
import AssetSearch from "./AssetSearch";
import AssetView from "./AssetView";
import ModelSelect from "./ModelSelect";
import Recommended from "./Recommended";
import ModelConfig from "./store/ModelConfig";

const AppCore = observer(({ modelConfig }: any) => {
  return <div>
    <Link to="/">Home</Link>
    <ModelSelect onChange={modelConfig.setModelId}/>
    <AssetSearch />
    <Routes>
      <Route path="/" element={<Recommended modelConfig={modelConfig} />} />
      <Route path="/ticker/:ticker" element={<AssetView modelConfig={modelConfig} />} />
    </Routes>
  </div>
});

export default (props: any) => <AppCore {...props} modelConfig={new ModelConfig()} />;