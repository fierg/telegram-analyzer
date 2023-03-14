import React, { useEffect, useState } from "react";
import { Col, Container, Form, Row, Tab, Tabs } from "react-bootstrap";
import { useParams } from "react-router-dom";
import { Bar, BarChart, ResponsiveContainer, XAxis, YAxis } from "recharts";
import { MessageDatesChart, MessagesByForwardChart, MessagesByTypeChart } from "../components/Charts";
import LoadingSpinner from '../components/LoadingSpinner';
import API from "../services/api";
import GroupMessages from "../components/GroupMessages";

const format = Intl.NumberFormat('en-US').format

function GroupPage() {
    const fetchGroup = (id, callback) => {
        API.getGroup(id).then(data => {
            callback(data);
        });
    }

    const { groupId } = useParams()
    const [group, setGroup] = useState()

    useEffect(() => {
        fetchGroup(groupId, setGroup)
    }, [])




    return group ? (
        <Container>

            <h1>{group.title}</h1>
            <span>{group.description}</span><br />
            <small>{format(group.memberCount)} 👥</small><br />

            <Tabs defaultActiveKey="overview" className="mb-3">
                <Tab eventKey="overview" title="Overview">
                    <Overview group={group} />
                </Tab>
                <Tab eventKey="messages" title="Messages">
                    <GroupMessages groupId={group.telegramId} />
                </Tab>
                <Tab eventKey="links" title="Links">
                    <Links group={group} />
                </Tab>
                <Tab eventKey="graph" title="Graph" disabled>
                    TODO
                </Tab>
            </Tabs>





        </Container>
    ) : <LoadingSpinner />;
}

const CustomizedLabel = ({ x, y, value }) => {
    return <text
        x={x * 2}
        y={y + 18}
        fill='#FFFFFF'
        fontSize='16'
        fontFamily='sans-serif'
        textAnchor="start">{value}</text>
}

const Links = ({ group }) => {
    const groupId = group.telegramId

    const [links, setLinks] = useState([])
    const fetchLinks = () => {
        API.get(`groups/${groupId}/domains`).then(setLinks)
    }

    useEffect(() => {
        fetchLinks()
    }, [])

    return <ResponsiveContainer className="link-chart" minWidth={700} height={links.length * 30}>
        <BarChart layout="vertical" barCategoryGap={1} data={links} margin={{ right: 40 }}>
            <Bar dataKey="count" fill="#8884d8" height={100} label={{ position: 'right', fill: 'antiquewhite' }} />
            <XAxis type="number" hide />
            <YAxis type="category" width={280} padding={{ left: 20 }} dataKey="domain" />
        </BarChart>
    </ResponsiveContainer>
}


const Overview = ({ group }) => <>
    <Row>
        <Col md={6}>
            <MessagesByTypeChart chatId={group.telegramId} />
        </Col>
        <Col md={6}>
            <MessagesByForwardChart chatId={group.telegramId} />
        </Col>
    </Row>
    <Row>
        <MessageDatesChart chatId={group.telegramId} />
    </Row></>

export default GroupPage;
