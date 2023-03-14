import React, { useEffect, useState } from 'react';
import { Bar, BarChart, CartesianGrid, Cell, Pie, PieChart, Tooltip, XAxis, YAxis } from "recharts";
import API from "../services/api";
import LoadingSpinner from './LoadingSpinner'

const COLORS = [
    '#003387',
    '#7a2f8e',
    '#c02780',
    '#ef3e62',
    '#ff6f3c',
    '#ffa600',
];

const RADIAN = Math.PI / 180;

const capitalizeFirstLetter = (string) => {
    return string.charAt(0).toUpperCase() + string.slice(1);
}

const renderCustomizedLabel = ({ cx, cy, midAngle, innerRadius, outerRadius, percent, index }, data) => {
    const radius = innerRadius + (outerRadius - innerRadius) * 1.1;
    const x = cx + radius * Math.cos(-midAngle * RADIAN);
    const y = cy + radius * Math.sin(-midAngle * RADIAN);

    return (
        <text x={x} y={y} fill="white" textAnchor={x > cx ? 'start' : 'end'} dominantBaseline="central">
            {`${capitalizeFirstLetter(data[index].name)}: ${(percent * 100).toFixed(0)}%`}
        </text>
    );
};

export const MessagesByTypeChart = ({ chatId }) => {

    const fetchData = (setMessageTypes) => {
        if (chatId) {
            API.get(`groups/${chatId}/messagetypes`).then(setMessageTypes)
        } else {
            API.get("stats/messagetypes").then(setMessageTypes)
        }
    }

    const [messageTypes, setMessageTypes] = useState(false)

    useEffect(() => {
        fetchData(setMessageTypes)
    }, [])


    const msgTypesData = Object.entries(messageTypes).map(it => ({
        name: it[0],
        value: it[1]
    }))

    return messageTypes ? <PieChart width={700} height={500}>
        <Pie
            isAnimationActive={false}
            data={msgTypesData}
            cx="50%"
            cy="50%"
            labelLine={true}
            label={d => renderCustomizedLabel(d, msgTypesData)}
            outerRadius={200}
            fill="#8884d8"
            dataKey="value"
        >
            {msgTypesData.map((entry, index) => (
                <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
            ))}
        </Pie>
    </PieChart> : <LoadingSpinner />
}

export const MessagesByForwardChart = ({ chatId }) => {
    const fetchData = (setMessageForwards) => {
        if (chatId) {
            API.get(`groups/${chatId}/messageforwards`).then(setMessageForwards)
        } else {
            API.get("stats/messageforwards").then(setMessageForwards)
        }
    }

    const [messageForwards, setMessageForwards] = useState([])

    useEffect(() => {
        fetchData(setMessageForwards)
    }, [])


    const msgForwardsData = messageForwards.map(it => ({
        name: it.forwarded ? 'Forwarded' : 'Unique',
        value: it.count
    }))

    return messageForwards.length > 0 ? <PieChart width={700} height={500}>
        <Pie
            isAnimationActive={false}
            data={msgForwardsData}
            cx="50%"
            cy="50%"
            labelLine={true}
            label={d => renderCustomizedLabel(d, msgForwardsData)}
            outerRadius={200}
            fill="#8884d8"
            dataKey="value"
        >
            {msgForwardsData.map((entry, index) => (
                <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
            ))}
        </Pie>
    </PieChart> : <LoadingSpinner />
}

export const MessageDatesChart = ({ chatId }) => {
    const fetchData = (callback) => {
        if (chatId) {
            API.get(`groups/${chatId}/messagesbydate`).then(callback)
        } else {
            API.get("messages/groupbydate").then(callback)
        }
    }

    const [messageDates, setMessageDates] = useState()

    useEffect(() => {
        fetchData(setMessageDates)
    }, [])

    if (!messageDates) {
        return <LoadingSpinner />
    }

    const data = messageDates.map(it => ({
        name: it.date,
        value: it.count
    }))

    return <div style={{ position: 'relative' }}>
        <div style={{ color: 'rgba(255,255,255,0.7)', zIndex: 10, position: 'absolute', textAlign: 'center', width: '100%', marginTop: '1em' }}>
            <h3>{(messageDates.reduce((a, b) => a + b.count, 0) / messageDates.length).toFixed(1)}&nbsp;&nbsp;<small>messages / day</small></h3>
        </div>
        <div >
            <BarChart
                width={1400}
                height={300}
                data={data}
            >
                <CartesianGrid strokeDasharray="1 2" stroke="rgba(255,255,255,0.1)" />
                <XAxis dataKey="name" />
                <YAxis />
                <Tooltip />
                <Bar dataKey="value" fill={COLORS[4]} stroke="none" />
            </BarChart>
        </div>
    </div>
}

