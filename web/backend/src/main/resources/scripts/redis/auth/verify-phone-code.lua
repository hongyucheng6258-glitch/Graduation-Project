local value = redis.call('GET', KEYS[1])
if not value then return 0 end
if string.sub(value,1,6) == ARGV[1] then
    redis.call('DEL', KEYS[1])
    return 1
end
local remaining = tonumber(string.sub(value,7)) - 1
local ttl = redis.call('PTTL', KEYS[1])
if remaining <= 0 or ttl <= 0 then redis.call('DEL', KEYS[1])
else redis.call('PSETEX', KEYS[1], ttl, string.sub(value,1,6)..remaining) end
return 0
