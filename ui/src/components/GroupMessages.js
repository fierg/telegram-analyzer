import { Form } from 'react-bootstrap'
import InfiniteScroll from 'react-infinite-scroll-component'
import API from '../services/api'
import LoadingSpinner from './LoadingSpinner'
import Message from './Message'
import React, { useEffect, useState } from "react";

const RESULTS_PER_PAGE = 10

const GroupMessages = ({ groupId }) => {

    const [loading, setLoading] = useState(true)
    const [searchQuery, setSearchQuery] = useState('')
    const [page, setPage] = useState(0)

    const fetchLastMessages = (query, id, page, callback) => {
        setPage(page)

        API.get(`groups/${id}/messages?offset=${page * RESULTS_PER_PAGE}&limit=${RESULTS_PER_PAGE}&q=${query}`)
            .then(data => {
                if (page === 0) {
                    callback(data);
                } else {
                    const newData = {
                        ...data,
                        results: messages.results.concat(data.results)
                    }
                    callback(newData)
                }
                setLoading(false)
            })
    }

    const [messages, setMessages] = useState({})

    useEffect(() => {
        fetchLastMessages("", groupId, 0, setMessages)
    }, [])

    const handleSearch = (newQuery) => {
        if (newQuery === searchQuery) return

        setPage(0)
        setSearchQuery(newQuery)

        if (newQuery.length > 2) {
            fetchLastMessages(newQuery, groupId, 0, setMessages)
        } else if (newQuery.length == 0) {
            fetchLastMessages("", groupId, 0, setMessages)
        }
    }

    if (loading) { return <LoadingSpinner /> }

    return <><Form.Control style={{ marginBottom: '1em' }} defaultValue={searchQuery} type="text" placeholder="Search..." onChange={e => handleSearch(e.target.value)} />
        <InfiniteScroll
            key={searchQuery}
            inverse={true}
            style={{ display: 'flex', flexDirection: 'column-reverse' }}
            height={'70vh'}
            dataLength={messages.results.length} //This is important field to render the next data
            next={() => fetchLastMessages(searchQuery, groupId, page + 1, setMessages)}
            hasMore={messages.results.length < messages.count}
            //loader={<LoadingSpinner />}
            scrollableTarget="scroll-div"
            endMessage={
                <p style={{ textAlign: 'center' }}>
                    <b>Yay! You have seen it all</b>
                </p>
            }
        >
            {messages.results.map(msg => <Message
                style={{ marginTop: '1em' }}
                message={msg}
                highlight={searchQuery}
                showGroupLink={false} />)}
        </InfiniteScroll> 
    </>
}

export default GroupMessages