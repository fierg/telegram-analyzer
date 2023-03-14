import './App.css';
import { Navbar, Container, Nav, NavDropdown } from 'react-bootstrap';
import { Routes, Route, Link } from 'react-router-dom'
import HomePage from './pages/HomePage'
import JobsPage from './pages/JobsPage';
import GroupPage from './pages/GroupPage';
import GroupSearchPage from './pages/GroupSearchPage';
import MessageSearchPage from './pages/MessageSearchPage';
import GraphPage from './pages/GraphPage';
import ChordLinkPage from "./pages/ChordLinkPage";

function App() {
  return (
    <div className="App">
      <Navbar sticky="top" expand="lg">
        <Container>
          <Link to="/"><Navbar.Brand>Quer Explorer</Navbar.Brand></Link>
          <Navbar.Toggle aria-controls="basic-navbar-nav" />
          <Navbar.Collapse id="basic-navbar-nav">
            <Nav className="me-auto">
              <Link to="/graph"><Nav.Item style={{ marginTop: '1em', margin: '1em' }}>Graph</Nav.Item></Link>
              <Link to="/chord"><Nav.Item style={{ marginTop: '1em', margin: '1em' }}>ChordLink</Nav.Item></Link>
              <Link to="/groups"><Nav.Item style={{ marginLeft: '2em', marginTop: '1em', margin: '1em' }}>Groups</Nav.Item></Link>
              <Link to="/messages"><Nav.Item style={{ marginTop: '1em', margin: '1em' }}>Messages</Nav.Item></Link>
              <Link to="/jobs"><Nav.Item style={{ marginTop: '1em', margin: '1em' }}>Jobs</Nav.Item></Link>
              {/*<NavDropdown title="Dropdown" id="basic-nav-dropdown">
                <NavDropdown.Item href="#action/3.1">Action</NavDropdown.Item>
                <NavDropdown.Item href="#action/3.2">Another action</NavDropdown.Item>
                <NavDropdown.Item href="#action/3.3">Something</NavDropdown.Item>
                <NavDropdown.Divider />
                <NavDropdown.Item href="#action/3.4">Separated link</NavDropdown.Item>
          </NavDropdown>*/}
            </Nav>
          </Navbar.Collapse>
        </Container>
      </Navbar>
      <br />
      <div style={{
        height: 'calc(100% - 3em)',
        width: '100vw',
        overflowY: 'hidden'

      }}>
        <Routes >
          <Route path="/" element={<HomePage />} />
          <Route path="/graph" element={<GraphPage />} />
          <Route path="/chord" element={<ChordLinkPage />} />
          <Route path="/jobs" element={<JobsPage />} />
          <Route path="/messages" element={<MessageSearchPage />} />
          <Route path="/groups" element={<GroupSearchPage />} />
          <Route path="/groups/:groupId" element={<GroupPage />} />
        </Routes>
      </div>
    </div >
  );
}

export default App;
