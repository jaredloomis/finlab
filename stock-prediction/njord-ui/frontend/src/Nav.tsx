import { observer } from "mobx-react-lite";
import { Link } from "react-router-dom";
import AssetSearch from "./AssetSearch";
import ModelSelect from "./ModelSelect";

import "./Nav.css"

export default observer(({ modelConfig }: any) => {
  return <div className="Nav-wrapper">
      <Link to="/">Home</Link>
      <AssetSearch />
      <ModelSelect onChange={modelConfig.setModelId}/>
  </div>
});
