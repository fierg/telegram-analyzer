-- JOBS COUNT BY TYPE + STATE
select type, state, count(*) from jobInfo
group by type, state
order by type, state

-- JOBS COUNT BY STATE
select state, count(*) from jobInfo
group by state
order by state

-- COUNT MESSAGES (where for unique messages)
select count(*) from message --where isForwarded = false

-- GROUPS OVERVIEW
select g.title, g.memberCount, case when g.isChannel = TRUE then '' else 'Chat' end as type, count(m.id) as messages
from groupChat g
join message m on g.telegramId=m.telegramChatId
group by g.title, g.memberCount, type
order by g.memberCount desc

-- TOP FORWARD ORIGINS
select g.title, count(m.id) as origin
from groupChat g
join message m on g.telegramId=m.forwardOriginChatId
group by g.title
order by origin desc

-- GET CONNECTION GRAPH
select count(concat(m.telegramChatId, m.forwardoriginchatid)) as connections, g.title, o.title
from message m
join groupchat g on g.telegramId=m.telegramChatId
join groupchat o on o.telegramId=m.forwardoriginchatid
where m.forwardoriginchatid != 0 and m.telegramChatId != m.forwardoriginchatid
group by g.title, o.title
order by connections desc
limit 200

-- FIND MESSAGES WITH MENTIONS
select t.*, m.*
from message m
join messagetextentities t
on t.messageid = m.id
where t.type='TextEntityTypeMention'
limit 10

-- FIND GROUPS FOUND BY INVITE LINK WITH NO REMAINING MESSAGE JOBS
select count(*)
from groupchat g
where g.telegramId in (
   select j.param1::bigint
   from jobinfo j
   where j.createdByClass = 'com.mkleimann.querscraper.job.impl.FollowInviteLinkJob_Subclass'
   and j.type = 'GetGroupInfo'
   and j.state = 'FINISHED')
and (
   select count(*)
   from jobinfo j
   where j.type = 'GetMessageHistory'
   and j.state in ('NEW', 'RUNNING')
   and j.param1::bigint = g.telegramId
) = 0

-- FIND PAYPAL LINKS
select count(url), url
from messagelink
where domain = 'paypal.me'
group by url
order by count desc

-- FIND MESSAGE JOBS FOR JOINED GROUPS
select count(*)
from jobinfo j
where j.type = 'GetMessageHistory'
and j.state in ('NEW')
and j.param1 in (
   select j.param1
   from jobinfo j
   where j.createdByClass = 'com.mkleimann.querscraper.job.impl.FollowInviteLinkJob_Subclass'
   and j.type = 'GetGroupInfo'
   and j.state = 'FINISHED')

-- FIND LINKS IN GROUP
select count(url), url
from messagelink
where chatId = <chat-id>
group by url
order by count desc

-- DEBUG: FIND NOT ADDED GROUPS AFTER JOINING INVITE LINK
select j.param1
from jobinfo j
where (select count(*) from groupchat where j.param1 = telegramId::varchar(255)) = 0
and j.createdByClass = 'com.mkleimann.querscraper.job.impl.FollowInviteLinkJob_Subclass'
