import React, {useEffect, useRef, useState} from "react";
import LoadingSpinner from "../components/LoadingSpinner";
import API from "../services/api";
import ChordLink from "../components/ChordLink";

function ChordLinkPage() {

    const [loading, setLoading] = useState(false)
    const errorMessage = "still loading..."

    return loading ? <>
        <LoadingSpinner/>
        {errorMessage && <h2>{errorMessage}</h2>}6
    </> : <ChordLink chartID="pie-chart"/>
}

export default ChordLinkPage;
