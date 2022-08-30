import { observer } from "mobx-react-lite";
import { Routes, Route } from "react-router-dom";
import AssetView from "./AssetView";
import Recommended from "./Recommended";
import Nav from "./Nav";
import ModelConfig from "./store/ModelConfig";
import PageContainer from "./PageContainer";

const AppCore = observer(({ modelConfig }: any) => {
  return <div>
    <Nav modelConfig={modelConfig} />
    <PageContainer>
      <Routes>
        <Route path="/" element={<Recommended modelConfig={modelConfig} />} />
        <Route path="/ticker/:ticker" element={<AssetView modelConfig={modelConfig} />} />
      </Routes>
    </PageContainer>
  </div>
});

export default (props: any) => <AppCore {...props} modelConfig={new ModelConfig()} />;