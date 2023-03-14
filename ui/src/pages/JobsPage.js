import React, { Component } from "react";
import { Container, Table } from "react-bootstrap";
import ChangeHighlight from "react-change-highlight";
import LoadingSpinner from "../components/LoadingSpinner";
import API from "../services/api";

var interval

class JobsPage extends Component {

    state = {

    }

    componentDidMount() {
        this.fetchStatus();
        this.fetchSettings();
        interval = setInterval(this.fetchStatus, 5000)
    }

    componentWillUnmount() {
        clearInterval(interval)
    }

    fetchStatus = () => {
        API.get("jobs/grouped").then(it => {
            this.setState({ jobState: it });
        });
    }

    fetchSettings = () => {
        API.get("jobs/settings").then(it => {
            this.setState({ jobSettings: it });
        });
    }

    sumStates = (states, state) =>
        states.reduce((acc, a) => acc + (a[1][state] || 0), 0)


    render() {
        if (!this.state.jobState) {
            return <LoadingSpinner />
        }
        const states = Object.entries(this.state.jobState).sort((a, b) => a[0] < b[0] ? -1 : 1)
        return (
            <Container>
                <Table striped bordered hover>
                    <thead onClick={this.fetchStatus} >
                        <tr>
                            <th>Job Type</th>
                            <th></th>
                            <th>Queued</th>
                            <th>Running</th>
                            <th>Skipped</th>
                            <th>Failed</th>
                            <th>Finished</th>
                        </tr>
                    </thead>
                    {this.state.jobState &&
                        <tbody>
                            {states.map(it => this.renderRow(it[0], it[1]))}
                            {this.renderStateSum(states)}
                        </tbody>}
                </Table>
            </Container>)
    }

    renderStateSum(states) {
        return this.renderRow("Σ", ({
            NEW: this.sumStates(states, "NEW"),
            RUNNING: this.sumStates(states, "RUNNING"),
            CANCELLED: this.sumStates(states, "CANCELLED"),
            ERROR: this.sumStates(states, "ERROR"),
            FINISHED: this.sumStates(states, "FINISHED")
        }), true);
    }

    handlePause = (type) => {
        API.post(`jobs/pause?type=${type}&pause=${!this.state.jobSettings[type]}`).then(it =>
            this.setState({ jobSettings: it })
        )
    }

    renderValueColumn = (value, isSum) => {
        return isSum ? <td>{value}</td> : <td><ChangeHighlight><div style={{ width: '100%', height: '100%' }} ref={React.createRef()}>{value}</div></ChangeHighlight></td>
    }

    renderRow = (type, jobState, sum = false) => {
        return <tr key={type}
            style={sum ?
                { fontWeight: "bolder" } :
                {}}>
            <td style={sum ? {} : { backgroundColor: this.state.jobSettings[type] ? 'darkred' : 'green' }}>{type}</td>
            <td style={{ margin: 0, padding: 0, fontSize: '1.8em', cursor: 'pointer' }} onClick={() => this.handlePause(type)}>{(sum || !this.state.jobSettings) ? '' : this.state.jobSettings[type] ? '▶️' : '⏸'}</td>
            {this.renderValueColumn(jobState["NEW"], sum)}
            {this.renderValueColumn(jobState["RUNNING"], sum)}
            {this.renderValueColumn(jobState["CANCELLED"], sum)}
            {this.renderValueColumn(jobState["ERROR"], sum)}
            {this.renderValueColumn(jobState["FINISHED"], sum)}
        </tr>
    }
}


export default JobsPage;
