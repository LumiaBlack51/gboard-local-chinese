-- Keep snapshots as strings. Large per-record Lua tables make the bundled
-- librime-lua collector scan the entire shared vocabulary between key events.
-- Find only rows for the current complete code, without changing the wire TSV.
local M = {}
local files = {}

function M.iter(name, code)
  local path = rime_api.get_user_data_dir() .. '/' .. name
  local file = io.open(path, 'r')
  if not file then return nil end
  local fingerprint = file:read('*l')
  local cached = files[path]
  if not cached or cached.fingerprint ~= fingerprint then
    cached = {fingerprint=fingerprint, body=file:read('*a') or ''}
    files[path] = cached
  end
  file:close()
  local body, needle, position = cached.body, '\t' .. code .. '\t', 1
  return function()
    while true do
      local first, last = body:find(needle, position, true)
      if not first then return nil end
      position = last + 1
      local finish = body:find('\n', position, true) or (#body+1)
      -- Protocol words have at most 32 Unicode characters (128 UTF-8 bytes).
      local start = math.max(1, first-128)
      local word = body:sub(start, first-1):match('([^\r\n]+)$')
      if word and not word:find('\t',1,true) and word:sub(1,1)~='#'
        and (start==1 or #word<128 or body:sub(start-1,start-1)=='\n') then
        return word, body:sub(position,finish-1):gsub('\r$','')
      end
    end
  end
end

return M
