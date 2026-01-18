群聊消息表设计:
id
group_id//所在会话中的群id
message text
create_time
group_member
role  //assistant 或 user
meta json

私聊消息表设计:
id
user_id //所在会话中对方的qqid
message text
role  //assistant 或 user
create_time
meta json
