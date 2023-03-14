import interpolate from "color-interpolate";
import { forceCollide, forceManyBody } from "d3-force";
import React, { useEffect, useRef, useState } from "react";
import { Button, Card, Col, Form, ListGroup, Row } from "react-bootstrap";
import ForceGraph from 'react-force-graph-2d';
import { Link } from 'react-router-dom';
import LoadingSpinner from "../components/LoadingSpinner";
import API from "../services/api";

const format = Intl.NumberFormat('en-US').format

const NODE_R = 0.05
const WARMUP_TICKS = 150

const COLOR_CLICKED_NODE = '#ffa600'
const COLOR_HOVER_NODE = '#FFFFFF'
const COLOR_HIGHLIGHTED_NODE = '#ef3e62'

function GraphPage() {
    const nodeColorMap = interpolate(["#ffaa33", "#9780ff"])
    const clusterColorMap =
        ['#ff0000', '#f8a4a4','#ff6202','#ffc400',
        '#a2ff00','#66ff00','#00ffc4','#0081ff',
        '#2200ff','#5e00ff','#bf00ff','#691294',
        '#d694e7','#671b37','#ffffff','#050000',]

    const fgRef = useRef();

    const [minLinkStrength, setMinLinkStrength] = useState(0.02);
    const [minConnections, setMinConnections] = useState(30);
    const [minMembers, setMinMembers] = useState(100);
    const [minMessages, setMinMessages] = useState(100);

    const [errorMessage, setErrorMessage] = useState()

    const setForces = (links) => {
        // fgRef.current.
        //     d3Force('link', forceLink(links)
        //  //       .strength(link => link.value))
        //     .distance(link => (1 / link.value) * 1000))
        fgRef.current.d3Force('collide', forceCollide(n => Math.sqrt(n.val) * NODE_R + 1))
        fgRef.current.d3Force('charge', forceManyBody())
        fgRef.current.d3Force('center', null)
    }

    const fetchGraph = (callback, setLoading) => {
        setLoading(true)
        API.get(`graph?connections=${minConnections}&members=${minMembers}&messages=${minMessages}&linkstrength=${minLinkStrength}`).then(it => {
            if (it.nodes.length > 1) {
                callback(it)
                setLoading(false)
                setErrorMessage(null)

                if (fgRef.current) setForces(it.links)
            } else {
                setErrorMessage("Graph is being recalculated, please wait...")
                setTimeout(() => fetchGraph(callback, setLoading), 1000)
            }
        })
    }

    const [graph, setGraph] = useState()
    const [loading, setLoading] = useState(true)

    useEffect(() => {
        fetchGraph(setGraph, setLoading)
    }, [])

    const [highlighted, setHighlighted] = useState({
        nodes: new Set(),
        links: new Set(),
        clickedNode: null,
        hoverNode: null
    })
    const [group, setGroup] = useState({});


    const handleSelectNode = (node, clicked) => {
        if (node) {
            fetchGroup(node.id)


            highlighted.nodes.clear();
            highlighted.links.clear();

            node.neighbors.forEach(neighbor => highlighted.nodes.add(neighbor));
            node.links.forEach(link => {
                highlighted.links.add(`${link.source}-${link.target}`)
            })

            highlighted.clickedNode = node
            fgRef.current.centerAt(node.x + 25, node.y, 800)

            setHighlighted({ ...highlighted, hoverNode: null });
        } else {
            setHighlighted({
                nodes: new Set(),
                links: new Set(),
                clickedNode: null,
                hoverNode: null
            })
        }

    };

    const getLinkColor = (link) => {
        //console.log(`${link.source}-${link.target}`,link, highlightLinks)
        if (highlighted.links.size === 0) {
            return `rgba(255,255,255,0.035)`
        } else {
            const highlight = isHighlighted(link)
            const alpha = (!highlighted.hoverNode
                || link.target.id == highlighted.hoverNode.id
                || link.source.id == highlighted.hoverNode.id) ? 0.7 : 0.2
            if (highlight === 1) { // out
                return `rgba(151, 128, 255,${alpha})`
            } else if (highlight === 2) { // in
                return `rgba(255, 170, 51, ${alpha})`
            }
        }

        return `rgba(255,255,255,0.02)`
    }

    const isHighlighted = (link) => {
        if (highlighted.links.has(`${link.source.id}-${link.target.id}`) || highlighted.links.has(`${link.target.id}-${link.source.id}`))
            return link.source.id === highlighted.clickedNode.id ? 1 : 2
        else return false
    }

    const outgoing = []
    const incoming = []

    const fetchGroup = (id) => {
        API.getGroup(id).then(setGroup)
    }

    if (highlighted.clickedNode) {
        highlighted.clickedNode.links.forEach(it => (it.source === highlighted.clickedNode.id) ? outgoing.push(it) : incoming.push(it))
    }

    const renderConnectionList = (key, connections, icon) => {
        return <ListGroup >{connections.sort((a, b) => b.value - a.value)
            .map(link => {
                const node = graph.nodes.find(n => n.id === link[key])
                return <ListGroup.Item onMouseLeave={() => {
                    highlighted.hoverNode = null
                    // if (highlighted.clickedNode)
                    //   fgRef.current.centerAt(highlighted.clickedNode.x + 25, highlighted.clickedNode.y + 20, 500)
                    setHighlighted({ ...highlighted })
                }} onMouseOver={() => {
                    highlighted.hoverNode = node
                    //  fgRef.current.centerAt(node.x + 25, node.y + 20, 500)
                    setHighlighted({ ...highlighted })
                }
                } className="graph-connection-list-item" style={{ cursor: 'pointer' }} key={node.id} onClick={() => {
                    handleSelectNode(node, true)

                }}>
                    <b>{node.name}</b><br />
                    <small>{Intl.NumberFormat('en-US').format(node.val)}&nbsp;👥&nbsp;&nbsp;{Intl.NumberFormat('en-US').format(link.connections)} x | {(link.value * 100).toFixed(2)} %&nbsp;{icon}</small>
                </ListGroup.Item>
            })}
        </ListGroup>
    }

    const renderGraphSettings = () => {
        return <>
            <h1>Search</h1>
            <Form>

                <Form.Group className="mb-3">
                    <Form.Label>Min Link Strength</Form.Label>
                    <Form.Control type="number" defaultValue={minLinkStrength} onChange={evt =>
                        setMinLinkStrength(evt.target.value)
                    } />
                </Form.Group>

                <Form.Group className="mb-3">
                    <Form.Label>Min Connections</Form.Label>
                    <Form.Control type="number" defaultValue={minConnections} onChange={evt =>
                        setMinConnections(evt.target.value)
                    } />
                </Form.Group>

                <Form.Group className="mb-3">
                    <Form.Label>Min Members</Form.Label>
                    <Form.Control type="number" defaultValue={minMembers} onChange={evt =>
                        setMinMembers(evt.target.value)
                    } />
                </Form.Group>

                <Form.Group className="mb-3">
                    <Form.Label>Min Messages</Form.Label>
                    <Form.Control type="number" defaultValue={minMessages} onChange={evt =>
                        setMinMessages(evt.target.value)
                    } />
                </Form.Group>

                <Button variant="primary" onClick={() => {
                    fetchGraph(setGraph, setLoading)
                }}>
                    Reload
                </Button>
            </Form>
        </>
    }

    const getNodeColor = (node, alpha) => {
        if (false) {
            if (node.cluster) return clusterColorMap[node.cluster]
            else return clusterColorMap[15]
        } else {
            if (highlighted.clickedNode) {
                const isHighlighted = highlighted.nodes.has(node.id)
                let color = node === highlighted.clickedNode ?
                    nodeColorMap(node.originalContent) :
                    (isHighlighted ? (nodeColorMap(node.originalContent)) : `rgba(20, 20, 20, 0.4)`)

                if (isHighlighted && highlighted.hoverNode && node !== highlighted.hoverNode && node !== highlighted.clickedNode) {
                    color = color.replace(/rgb\(/i, "rgba(");
                    color = color.replace(/\)/i, `,0.3)`);
                }

                return color
            }

            return nodeColorMap(node.originalContent)
        }

    }



    const renderLegend = () => {
        const steps = []
        for (let i = 10; i > 0; i--) {
            steps.push(<div style={{ width: '1.2em', height: '1em', backgroundColor: nodeColorMap(i / 10) }} />)
        }
        return <div style={{
            zIndex: 10,
            textAlign: 'center',
            position: "absolute",
            right: '1em',
            bottom: '1em',
            display: 'grid',
            gridTemplateColumns: '1em 1em',
            gridTemplateRows: 'repeat(10, 1em)',
            gridColumnGap: '1em',
            gridRowGap: '0px'
        }}>
            <div style={{ gridRow: '1 / 9' }} title="100% Original Content">💭</div>
            <div style={{ gridRow: '9 / 10', marginTop: '0.5em' }} title="100% Forwarded Content">🔁</div>

            <div>
                {steps}
            </div>
        </div>
    }

    return loading ? <>
        <LoadingSpinner />
        {errorMessage && <h2>{errorMessage}</h2>}
    </> :
        <div className="disable-selection" style={{ overflow: 'hidden', marginTop: '-1em', height: 'calc(100vh - 68px)' }}><Row >
            <Col key={highlighted.clickedNode ? highlighted.clickedNode.id : 'search'}
                style={{
                    overflowX: 'hidden',
                    overflowY: 'scroll',
                    //marginLeft: '0.5em',
                    maxHeight: '100vh'
                }} lg={2}>
                {highlighted.clickedNode ? <><Card>
                    <Card.Header >
                        <Link target="_blank" to={`/groups/${highlighted.clickedNode.id}`}><h2>{highlighted.clickedNode.name}</h2></Link>
                        <small title="Members">{format(highlighted.clickedNode.val)}&nbsp;👥</small>&nbsp;&nbsp;&nbsp;
                        <small title="Original Content">{format(Math.floor(highlighted.clickedNode.originalContent * 100))} % 💭</small>&nbsp;&nbsp;&nbsp;
                        {group && <small title={group.channel ? "Channel: only the owner can write" : "Chat: everyone can write"}>{group.channel ? "Channel 🙊" : "Chat 🤬"}</small>}
                    </Card.Header>
                    {group && <Card.Body>
                        <small>{group.description}</small>
                    </Card.Body>}
                </Card>

                    <section style={{
                        marginTop: '0.5em',
                        display: 'flex',
                        flexDirection: 'column',
                    }}>
                        {incoming.length > 0 && <>
                            <h3>Top Sources</h3>
                            {renderConnectionList('source', incoming, '⬇️')}
                        </>}
                    </section>
                    <section style={{
                        marginTop: '0.5em',
                        display: 'flex',
                        flexDirection: 'column',
                        marginBottom: '8em'
                    }}>{outgoing.length > 0 && <>
                        <h3>Most forwarded by</h3>
                        {renderConnectionList('target', outgoing, '🔁')}
                    </>}
                    </section>
                </> : renderGraphSettings()}
            </Col>
            <Col lg={10}>
                {renderLegend()}
                <ForceGraph
                    style={{ overflow: 'hidden' }}
                    id="graph"
                    ref={fgRef}
                    linkColor={getLinkColor}
                    nodeRelSize={NODE_R}
                    autoPauseRedraw={true}
                    graphData={graph}
                    nodeVal={node => {
                        const value = node.val === 0 ? 20000 : node.val//(node.val < 10000 ? 10000 : node.val)
                        return value
                    }}
                    linkDirectionalArrowLength={link => isHighlighted(link) ? 10 : 5}
                    linkDirectionalArrowRelPos={link => isHighlighted(link) === 2 ? 0.08 : 1}
                    linkDirectionalArrowColor={getLinkColor}

                    // linkDirectionalParticles={link => isHighlighted(link)}
                    // linkDirectionalParticlesWidth={5}

                    linkWidth={link => (highlighted.hoverNode && (
                        link.target.id == highlighted.hoverNode.id
                        || link.source.id == highlighted.hoverNode.id)) ? 2.5 : 1}
                    linkCurvature={0.15}
                    nodeColor={getNodeColor}
                    onNodeClick={node => handleSelectNode(node, true)}
                    onBackgroundClick={() => {
                        handleSelectNode()
                    }}
                    onLinkClick={() => {
                        handleSelectNode()
                    }}
                    enableNodeDrag={false}
                    onNodeDrag={() => undefined}
                    onNodeDragEnd={() => undefined}
                    warmupTicks={WARMUP_TICKS}
                    cooldownTime={15000}
                    // minZoom={1}
                    nodeLabel={node => `${node.name}<br/><small>${format(node.val)}&nbsp;👥&nbsp;&nbsp;&nbsp;${format(Math.floor(node.originalContent * 100))} % 💭</small>`}

                //onLinkHover={handleLinkHover}

                />
            </Col>
        </Row ></div >
}

export default GraphPage;
