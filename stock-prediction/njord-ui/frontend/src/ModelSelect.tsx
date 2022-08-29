import React, { useEffect, useState } from "react";

interface ModelSelectProps {
  onChange: (value: string) => void;
}

export default function ModelSelect({ onChange }: ModelSelectProps) {
  const [modelIds, setModelIds] = useState([] as string[]);

  // Download all predictions, parse models
  // TODO create a dedicated server endpoint - this is inefficient.
  useEffect(() => {
    (async function() {
      const res = await fetch(`/api/model_ids`);
      setModelIds(await res.json());
    })();
  }, []);

  return <select defaultValue="" onChange={e => onChange(e.target.value)}>
    <option disabled value=""> -- Select a Model to see Predictions -- </option>
    {modelIds.map(model_id =>
      <option value={model_id} key={model_id}>{model_id}</option>
    )}
  </select>;
}
