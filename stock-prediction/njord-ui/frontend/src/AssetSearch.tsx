import React, { useState } from "react";
import { useNavigate } from "react-router-dom";

interface AssetSearchProps {
}

const AssetSearch = (props: AssetSearchProps) => {
  const navigate = useNavigate();
  const [search, setSearch] = useState("");

  const submit = () => {
    navigate(`/ticker/${search.toUpperCase()}`);
  };

  return <div>
    <form onSubmit={submit}>
      <input type="text" onChange={e => setSearch(e.target.value)} />
    </form>
  </div>;
}

export default AssetSearch;
