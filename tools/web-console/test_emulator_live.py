"""No external network/game input: WebSocket frame parser + JPEG server helpers."""
import io
from pathlib import Path
import struct
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parent))
import emulator_live as live


def packet(opcode, payload):
    length=len(payload)
    if length<126:
        return bytes([0x80|opcode,length])+payload
    if length<=65535:
        return bytes([0x80|opcode,126])+struct.pack('>H',length)+payload
    return bytes([0x80|opcode,127])+struct.pack('>Q',length)+payload


class LiveVideoProtocolTests(unittest.TestCase):
    def test_h264_annexb_from_semeta_v2_after_text_handshake(self):
        annex=b'\x00\x00\x00\x01\x67'+bytes(range(21))
        meta=b'SEMU'+bytes((2,1))+b'\x00\x00'+struct.pack('>QQ',10,20)
        stream=io.BytesIO(packet(1,b'{"type":"video-session"}')+packet(2,meta+annex))
        self.assertEqual(live._next_ws_video(stream),annex)

    def test_legacy_unframed_annexb_is_supported(self):
        annex=b'\x00\x00\x01\x65'+b'abcde'
        self.assertEqual(live._next_ws_video(io.BytesIO(packet(2,annex))),annex)

    def test_oversized_websocket_message_is_rejected(self):
        length=live.MAX_VIDEO_FRAME+1
        stream=io.BytesIO(bytes([0x82,127])+struct.pack('>Q',length))
        with self.assertRaises(ValueError):
            live._next_ws_video(stream)

    def test_connection_close_is_not_a_picture(self):
        with self.assertRaises(ConnectionError):
            live._next_ws_video(io.BytesIO(packet(8,b'')))

    def test_live_session_limit(self):
        self.assertEqual(len(live.CERT_SHA256),64)
        self.assertEqual(live.BOUNDARY,b'liveframe')
        # No tests connect to/modify the running Android emulator.


if __name__=='__main__':unittest.main()
