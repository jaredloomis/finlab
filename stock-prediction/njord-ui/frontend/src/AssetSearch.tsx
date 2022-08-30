import React, { useState } from "react";
import { useNavigate } from "react-router-dom";

import "./AssetSearch.css"

interface AssetSearchProps {
}

const AssetSearch = (props: AssetSearchProps) => {
  const navigate = useNavigate();
  const [search, setSearch] = useState("");

  const submit = () => {
    navigate(`/ticker/${encodeURIComponent(search.toUpperCase())}`);
  };

  return <div>
    <form onSubmit={e => { e.preventDefault(); submit(); }}>
      <input type="text" placeholder="Ex. 'SPY'"onChange={e => setSearch(e.target.value)} />
    </form>
  </div>;
}

export default AssetSearch;
