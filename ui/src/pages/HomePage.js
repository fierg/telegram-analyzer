import axios from "axios";
import React, { PureComponent, useEffect, useMemo, useState } from "react";
import { Col, Container, Row } from "react-bootstrap";
import Animate from "react-smooth/lib/Animate";
import { Bar, BarChart, CartesianGrid, Cell, Pie, PieChart, Tooltip, XAxis, YAxis } from 'recharts';
import LoadingSpinner from "../components/LoadingSpinner";
import { MessagesByTypeChart, MessagesByForwardChart, MessageDatesChart } from "../components/Charts";
import API from "../services/api";

const format = Intl.NumberFormat('en-US').format

const COLORS = [
    '#003387',
    '#7a2f8e',
    '#c02780',
    '#ef3e62',
    '#ff6f3c',
    '#ffa600',
];


const HomePage = () => {

    /* const renderRow = (group, idx) => {
         return <tr>
            <td>{idx + 1}</td>
            <td><Link to={`/groups/${group.id}`}>{group.title}</Link></td>
            <td>{Intl.NumberFormat('en-US').format(group.memberCount)}</td>
        </tr>
     }*/

    const fetchData = (setGroupCount, setMessageCount) => {
        API.get("groups/count").then(setGroupCount);
        API.get("messages/count").then(setMessageCount)
    }

    const [groupCount, setGroupCount] = useState()
    const [messageCount, setMessageCount] = useState()

    useEffect(() => {
        fetchData(setGroupCount, setMessageCount)
        // setInterval(() => fetchData(setGroupCount, setMessageCount), 5000)
    }, [])

    return (
        <Container className="disable-select">

            {<h1>{groupCount ? format(groupCount) : '...'} <small> groups</small></h1>}
            {<h3>{messageCount ? format(messageCount) : '...'} <small> messages</small></h3>}


            <Row>
                <Col md={6}>
                    <MessagesByTypeChart />
                </Col>
                <Col md={6}>
                    <MessagesByForwardChart/>
                </Col>
            </Row>
            <Row>
                <MessageDatesChart />
            </Row>


            {/*<Table striped bordered hover>
                <thead>
                    <tr>
                        <th>#</th>
                        <th>Name</th>
                        <th>Followers</th>
                    </tr>
                </thead>
                {this.state.groupByFollowers &&
                    <tbody>
                        {this.state.groupByFollowers
                            .map((it, idx) => this.renderRow(it, idx))}
                    </tbody>}
                        </Table>*/}
        </Container>
    )
}


export default HomePage;
