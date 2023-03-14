import React, { useEffect, useRef, useState } from "react";
import { Form, Table } from "react-bootstrap";
import InfiniteScroll from "react-infinite-scroll-component";
import { Link, useNavigate, useLocation } from "react-router-dom";
import LoadingSpinner from "../components/LoadingSpinner";
import API from "../services/api";


let timeout = false

const RESULTS_PER_PAGE = 20

const format = Intl.NumberFormat('en-US').format

function GroupSearchPage() {
    const navigate = useNavigate()

    const handleGroupLink = (group) => {
        navigate(`/groups/${group.id}`)
    }

    const [deletedGroups, setDeletedGroups] = useState([])

    const handleDelete = (group) => {
        API.post(`groups/${group.id}/delete`).then(() => {
            const deleted = [...deletedGroups].concat([group.id])
            setDeletedGroups(deleted)
        })
    }

    const searchGroup = (query, callback, immediate = false) => {
        setPage(0)

        if (timeout) {
            clearTimeout(timeout)
        }

        if (immediate) {
            doSearch(query, 0, callback);
        } else {
            timeout = setTimeout(() => doSearch(query, 0, callback), 500)
        }

    }

    const searchParams = new URLSearchParams(useLocation().search)
    let parsedQuery = searchParams.get('query')
    if (parsedQuery === null) {
        parsedQuery = ""
    }

    const [groups, setGroups] = useState()
    const [searchQuery, setSearchQuery] = useState(parsedQuery)
    const [page, setPage] = useState(0)
    const [loading, setLoading] = useState(true)

    useEffect(() => {
        searchGroup(searchQuery, setGroups)
    }, [])

    const doSearch = (query, page, callback) => {
        setSearchQuery(query)
        setPage(page)

        API.get(`groups/search?q=${query}&offset=${page * RESULTS_PER_PAGE}&limit=${RESULTS_PER_PAGE}`).then(data => {
            if (page === 0) {
                callback(data);
            } else {
                const newData = {
                    ...data,
                    results: groups.results.concat(data.results)
                }
                callback(newData)
            }
            setLoading(false)
        });
    }

    const handleSearch = (newQuery) => {
        if (newQuery === searchQuery) return

        searchParams.set('query', newQuery)
        window.history.replaceState(null, "", `${window.location.protocol}//${window.location.host}${window.location.pathname}?query=${newQuery ? newQuery : ''}`)

        setPage(0)

        if (newQuery.length > 2) {
            setLoading(true)
            searchGroup(newQuery, setGroups)
        } else if (newQuery.length == 0) {
            setLoading(true)
            searchGroup("", setGroups)
        }
    }

    const containerRef = useRef()

    console.log(containerRef)

    return <div
        ref={containerRef}
        style={{
            display: 'grid',
            gridTemplateColumns: '1fr 6fr',
            gridColumnGap: '1em',
            margin: '0em 1em 0em 1em',
            height: 'inherit',
            whiteSpace: 'pre-wrap',
        }}>
        <div style={{ backgroundColor: 'rgba(20,20,20,0.5)', padding: '1em' }}>
            <h3>Search Groups</h3>
            <Form.Control defaultValue={searchQuery} type="text" placeholder="Search..." onChange={e => handleSearch(e.target.value)} />
            <div style={{ textAlign: 'right' }}>{<small >{groups ? format(groups.count) : 0} results ({groups ? format(groups.took) : 0} ms)</small>}</div>
        </div>
        <div style={{ overflowY: 'scroll' }}>
            {loading ? <LoadingSpinner /> :
                ((groups && groups.results) && <InfiniteScroll
                    key={searchQuery}
                    height={containerRef.current.offsetHeight + "px"}
                    dataLength={groups.results.length} //This is important field to render the next data
                    next={() => doSearch(searchQuery, page + 1, setGroups)}
                    hasMore={groups.results.length < groups.count}
                    loader={<LoadingSpinner />}
                    endMessage={
                        groups.count === 0 && <p style={{ textAlign: 'center' }}>
                            <br /><h1>Nothing found</h1>
                        </p>
                    }
                >
                    <Table striped bordered hover style={{ width: '100%', textOverflow: 'ellipsis' }}>
                        <thead >
                            <tr >
                                <th>Title</th>
                                {/* <th>Chat</th> */}
                                <th>Members</th>
                                <th>Messages</th>
                                <th>Description</th>
                                <th>Delete</th>
                            </tr>
                        </thead>
                        <tbody >
                            {groups.results.map(group =>
                                <tr style={{ cursor: 'pointer', textDecoration: deletedGroups.includes(group.id) ? 'line-through' : undefined }}
                                    title={group.description}>
                                    <td onClick={() => handleGroupLink(group)} style={{ maxWidth: '40em' }}>{group.title}</td>
                                    {/* <td>{group.isChannel ? 'Channel' : 'Chat'}</td> */}
                                    <td onClick={() => handleGroupLink(group)} >{format(group.memberCount)}</td>
                                    <td onClick={() => handleGroupLink(group)} >{group.messageCount === 0 ? '-' : format(group.messageCount)}</td>
                                    <td onClick={() => handleGroupLink(group)} style={{ fontWeight: 'lighter' }}>
                                        {firstLineOfDescription(group.description)}</td>
                                    <td onClick={() => handleDelete(group)}>❌</td>
                                </tr>
                            )}</tbody>
                    </Table>
                </InfiniteScroll>)}


        </div>
    </div>

}

const firstLineOfDescription = (description) => {
    const newLine = description.trim().indexOf("\n")
    if (newLine > -1) {
        return description.substring(0, newLine)
    }
    return description
}

/**
 * 
 *   {groups && <>
                    {groups.results && <InfiniteScroll
                        key={searchQuery}
                        // style={{ marginBottom: '50em' }}
                        height={'82vh'}
                        dataLength={groups.results.length} //This is important field to render the next data
                        next={() => doSearch(searchQuery, page + 1, setGroups)}
                        hasMore={groups.results.length < groups.results}
                        loader={<LoadingSpinner />}
                        endMessage={
                            groups.count === 0 && <p style={{ textAlign: 'center' }}>
                                <h1>Nothing found</h1>
                            </p>
                        }
                    >
                        {groups.results.map(group => <Link to={`/groups/${group.id}`}>
                            <ListGroupItem>
                                <Row>
                                    <Col md={4} style={{ borderRight: "solid rgba(1,1,1,0.4)" }}>
                                        <b>{group.title}</b><br />
                                        <small>{Intl.NumberFormat('en-US').format(group.memberCount)}&nbsp;👥</small>&nbsp;&nbsp;&nbsp;
                                        <small>{Intl.NumberFormat('en-US').format(group.messageCount)}&nbsp;💬</small> <br />
                                    </Col>
                                    <Col>
                                        {group.description}
                                    </Col>
                                </Row>
                            </ListGroupItem></Link>)}
                    </InfiniteScroll>}
                </>}
 * 
 */

export default GroupSearchPage;
