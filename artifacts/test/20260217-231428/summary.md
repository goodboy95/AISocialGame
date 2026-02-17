# Self Test Summary (2026-02-17)

- Frontend entry tested: http://127.0.0.1:10030/
- Room pages tested:
  - /room/undercover/mock-room
  - /room/werewolf/mock-room
- Text inputs executed:
  - 卧底房间聊天: "我觉得先观察一轮，再决定投票对象。"
  - 狼人房间聊天: "白天我会重点关注发言前后矛盾的玩家。"
  - 狼人房间聊天(复测): "今晚请注意投票节奏，不要被带偏。"
- Backend start: failed (datasource connection refused), 20030 not listening.
- Evidence:
  - screenshots (*.png)
  - playwright-console.log
  - playwright-network.log
  - backend.out.log / backend.err.log
