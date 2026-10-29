-- Keep large synchronized snapshots as at most 702 compact strings. librime-lua
-- runs a full GC when translations are destroyed, so retaining one Lua table per
-- word makes every key rescan the whole dictionary even when no rows match.
local M = {}
function M.read(file, parse)
  local groups = {}
  for line in file:lines() do
    local code, payload = parse(line)
    if code then
      local key = code:sub(1,2)
      local group = groups[key]
      if not group then group = {}; groups[key] = group end
      group[#group+1] = code .. '\t' .. payload
    end
  end
  for key, group in pairs(groups) do
    groups[key] = '\n' .. table.concat(group, '\n') .. '\n'
  end
  return groups
end
function M.rows(groups, code)
  local data, needle, position = groups[code:sub(1,2)] or '', '\n' .. code .. '\t', 1
  return function()
    local first, last = data:find(needle, position, true)
    if not first then return nil end
    local finish = data:find('\n', last+1, true)
    position = finish
    return data:sub(last+1, finish-1)
  end
end
return M
