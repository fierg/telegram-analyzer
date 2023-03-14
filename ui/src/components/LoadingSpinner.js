import { BallTriangle } from "react-loader-spinner"

const LoadingSpinner = () =>
    <div style={{ height: 400, width: '100%', display: 'flex', justifyContent: 'center', marginTop:'10%' }}>
        <BallTriangle style={{ margin: 'auto' }} color="#00BFFF" height={100} width={100} />
    </div>

export default LoadingSpinner