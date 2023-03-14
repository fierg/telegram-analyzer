import React, { useEffect, useState } from "react";
import { Container, Form } from "react-bootstrap";
import InfiniteScroll from "react-infinite-scroll-component";
import { Link, useLocation } from "react-router-dom";
import GroupMessages from "../components/GroupMessages";
import LoadingSpinner from "../components/LoadingSpinner";
import Message from "../components/Message";
import API from "../services/api";

const RESULTS_PER_PAGE = 10

let timeout

function MessageSearchPage() {

    const searchParams = new URLSearchParams(useLocation().search)
    const [messages, setMessages] = useState({ results: new Array(0) })

    const doSearch = (query, page, callback) => {
        setPage(page)
        setSearchQuery(query)

        searchParams.set('query', query)
        window.history.pushState(null, "", `${window.location.protocol}//${window.location.host}${window.location.pathname}?query=${query ? query : ''}`)

        if (!query) {
            callback([])
        } else {
            API.get(`messages/search?q=${query}&offset=${page * RESULTS_PER_PAGE}&limit=${RESULTS_PER_PAGE}`).then(data => {
                if (page === 0) {
                    callback(data);
                } else {
                    const newData = {
                        ...data,
                        results: messages.results.concat(data.results)
                    }
                    callback(newData)
                }
            });
        }
    }

    const searchMessages = (query, page, callback, immediate = false) => {
        if (timeout) {
            clearTimeout(timeout)
        }

        if (immediate) {
            doSearch(query, page, callback);
        } else {
            timeout = setTimeout(() => doSearch(query, page, callback), 500)
        }
    }

    const query = searchParams.get('query');

    const [searchQuery, setSearchQuery] = useState(query)
    const [page, setPage] = useState(0)
    const [selectedMessage, setSelectedMessage] = useState(null)
    const [selectedGroup, setSelectedGroup] = useState()

    useEffect(() => {
        searchMessages(query, 0, setMessages)
    }, [])

    const handleSearch = (newQuery) => {
        if (newQuery === searchQuery) return

        setPage(0)

        if (newQuery.length > 2) {
            searchMessages(newQuery, 0, setMessages)
        } else if (newQuery.length == 0) {
            searchMessages("", 0, setMessages)
        }
    }

    const fetchGroup = (chatId) => {
        API.getGroup(chatId).then(setSelectedGroup)
    }

    const handleSelectMessage = (message) => {
        console.log("select ", message)
        setSelectedMessage(message)
        fetchGroup(message.telegramChatId)
    }

    return <Container style={{ overflow: 'hidden' }}>
        <Form.Control defaultValue={searchQuery} type="text" placeholder="Search..." onChange={e => handleSearch(e.target.value)} />

        <div style={{ textAlign: 'right' }}>{messages.count && <small >{messages.count} results ({messages.took} ms)</small>}</div>

        <div style={{
            height: 'calc(100vh - 160px)',
            display: 'grid',
            gridTemplateColumns: 'repeat(2, 1fr)',
            gridTemplateRows: '1fr',
            gridColumnGap: '1em'
        }}>
            <div style={{ backgroundColor: 'rgba(20,20,20,0.5)', padding: '1em', paddingBottom: '5em' }}>
                <h2>Search Results</h2>
                {messages.results && <InfiniteScroll
                    key={searchQuery}
                    // style={{ marginBottom: '50em' }}
                    height={'82vh'}
                    dataLength={messages.results.length} //This is important field to render the next data
                    next={() => doSearch(searchQuery, page + 1, setMessages)}
                    hasMore={messages.results.length < messages.count}
                    loader={<LoadingSpinner />}
                    endMessage={
                        <p style={{ textAlign: 'center' }}>
                            <b>Yay! You have seen it all</b>
                        </p>
                    }
                >
                    {messages.results.map(msg => <><Message
                        selected={selectedMessage === msg}
                        onClick={() => handleSelectMessage(msg)}
                        key={`${msg.telegramChatId}-${msg.id}-${msg.telegramAuthorId}`}
                        message={msg}
                        highlight={searchQuery}
                        showGroupLink={true} /></>)}
                </InfiniteScroll>}
            </div>
            <div style={{ backgroundColor: 'rgba(20,20,20,0.5)', padding: '1em' }}>

                {(selectedMessage && selectedGroup) ?
                    <>
                        <h3><Link target="_blank" to={`/groups/${selectedMessage.telegramChatId}`}>{selectedGroup.title}</Link></h3></> : <h2>Context</h2>}
                {selectedMessage && <GroupMessages key={selectedMessage.telegramChatId} groupId={selectedMessage.telegramChatId} />}
            </div>
        </div >
    </Container >
}

export default MessageSearchPage;
