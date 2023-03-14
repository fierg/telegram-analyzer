import moment from "moment"
import { useEffect, useState } from 'react'
import ReactAudioPlayer from "react-audio-player"
import { Card, Col, Container, Image } from "react-bootstrap"
import Highlighter from "react-highlight-words"
import ReactPlayer from "react-player/lazy"
import { Link } from "react-router-dom"
import API from "../services/api"

const regexYoutubeLink = /((?:https?:)?\/?\/?(?:www|m)?\.?(?:youtube\.com|youtu.be)\/(?:[\w\-]+\?v=|embed\/|v\/)?[\w\-]+\S+)/g

const renderVideoPlayer = (url, width, height) => {

    const match = url.match(regexYoutubeLink)
    const isYoutubeLink = (match && match.length > 0) === true

    return <><div style={{
        cursor:'pointer',
        backgroundColor: 'rgba(20,20,20,0.5)',
        marginBottom: isYoutubeLink ? 0 : '1em',
        position: 'relative',
        paddingTop: (width && height) ? `${(100 / (width / height))}%` : undefined //todo: check if this works
    }}>
        <ReactPlayer
            controls
            style={{
                position: 'absolute',
                top: 0,
                left: 0
            }}
            playing={false}
            light//{!isYoutubeLink}
            width="100%"
            height="100%"
            playsinline={true}
            config={{
                youtube: {
                    playerVars: {
                        showinfo: 1,
                        origin: 'https://www.youtube.com'
                    },
                    embedOptions: {
                        enablejsapi: true
                    }
                }
            }}
            url={url} >
        </ReactPlayer>
    </div >
        {/* {isYoutubeLink && <><small style={{ marginTop: '-1em' }}><a href={url} target="_blank">{url}</a></small></>} */}
    </>
}

const MessageFile = ({ message }) => {
    if (message.messageType === 'MessagePhoto') {
        return <Container style={{ marginBottom: '1em', maxWidth: '100%' }}>
            <Image fluid
                src={getDownloadUrl(message)}>
            </Image>
        </Container >
    } else if (message.messageType === 'MessageVideo' ||
        message.messageType === 'MessageVideoNote') {
        return renderVideoPlayer(getDownloadUrl(message), message.width, message.height)
    } else if (message.messageType === 'MessageAudio' ||
        message.messageType === 'MessageVoiceNote') {
        return <Container style={{ marginBottom: '1em' }}><ReactAudioPlayer
            controls
            src={getDownloadUrl(message)}
            preload="none"
        /></Container>

    } else {
        return null
    }
}

const MessageContent = ({ message, highlight }) => {
    return <>
        <MessageFile message={message} />

        {message.textContent.split(regexYoutubeLink).map(part => {

            if (part.match(regexYoutubeLink) && !part.match(/\/(?:channel|c)\//)) {
                let url = part.substring(part.indexOf("youtu"))
                if (!url.startsWith("https://") && !url.startsWith("http://")) {
                    url = "https://" + url
                }
                return <><br />{renderVideoPlayer(url, 1280, 720)}<br /></>
            }

            return <div className="dontbreakout" style={{ whiteSpace: 'pre-line' }}>
                <Highlighter
                    highlightClassName="highlight"
                    textToHighlight={part}
                    searchWords={highlight.split(" ")}
                    autoEscape={false}
                />
            </div>

        })}
    </>
}

const Message = ({ message, highlight, showGroupLink, onClick, selected }) => {

    const [group, setGroup] = useState()

    useEffect(() => {
        if (showGroupLink)
            API.getGroup(message.telegramChatId).then(setGroup)
    }, [])

    return <Card onClick={onClick} style={{ marginBottom: '1em', cursor: onClick ? 'pointer' : undefined }}>
        <Card.Header style={{ backgroundColor: selected ? 'rgba(200,100,0,0.4)' : 'rgba(20,20,20,0.5)' }}>
            {showGroupLink && (group ? 
                <Col><b><Link target={"_blank"} to={`/groups/${message.telegramChatId}`}>{group.title}</Link></b><small style={{ float: "right" }}>&nbsp;&nbsp;{Intl.NumberFormat('en-US').format(group.memberCount)}&nbsp;👥</small></Col> : "...")}
            <small>{message.messageType.substring('Message'.length)}&nbsp;{message.isForwarded && "(Forwarded)"}</small>&nbsp;|&nbsp;<small>{moment(message.date).format("LLLL")} ({moment(message.date).fromNow()})</small>
        </Card.Header>
        <Card.Body style={{ backgroundColor: selected ? 'rgba(200,100,0,0.1)' : undefined }}>
            <div><MessageContent message={message} highlight={highlight} /></div>
            <b>{message.groupChatId}</b>
        </Card.Body>
    </Card>
}


const getDownloadUrl = (message) => {
    return `${API.url()}/groups/${message.telegramChatId}/messages/${message.id}/downloadfile`
}

export default Message


