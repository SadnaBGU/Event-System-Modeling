import { v4 as uuidv4 } from 'uuid';

export const getGuestSessionId = (): String => {
    let sessionId = sessionStorage.getItem('guest_session_id');
    if (!sessionId) {
        sessionId = uuidv4();
        sessionStorage.setItem('guest_session_id', sessionId);
    }
    return sessionId;
};