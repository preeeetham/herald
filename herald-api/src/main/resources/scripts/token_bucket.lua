-- Atomic token bucket. Uses the Redis server clock so API replicas never disagree on time.
-- KEYS[1]  bucket key
-- ARGV[1]  refill rate (tokens per second)
-- ARGV[2]  capacity (burst)
-- ARGV[3]  tokens requested
-- Returns  { allowed (0|1), remaining tokens (floor), retry-after ms }
local rate = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])

local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)

local state = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
local tokens = tonumber(state[1]) or capacity
local ts = tonumber(state[2]) or now

tokens = math.min(capacity, tokens + math.max(0, now - ts) * rate / 1000)

local allowed = 0
local retry_ms = 0
if tokens >= requested then
  tokens = tokens - requested
  allowed = 1
else
  retry_ms = math.ceil((requested - tokens) * 1000 / rate)
end

redis.call('HSET', KEYS[1], 'tokens', tokens, 'ts', now)
-- An idle bucket refills completely after capacity/rate seconds; expire it after that.
redis.call('PEXPIRE', KEYS[1], math.ceil(capacity * 1000 / rate) + 1000)
return { allowed, math.floor(tokens), retry_ms }
