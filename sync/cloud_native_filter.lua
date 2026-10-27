-- Preserve native Phrase identity/learning when a cloud row repeats a native
-- candidate. Deduplicate display text and prefer the candidate consuming more
-- input. Bound lookahead so a long native candidate stream stays lazy.
local M = {}
local function cloud(c) return c:get_genuine().type == 'cloud_phrase' end
local function preferred(a,b)
  if a._end~=b._end then return a._end>b._end end
  if cloud(a)~=cloud(b) then return not cloud(a) end
  return a.quality>b.quality
end
local function outranks(a,b)
  if a._end~=b._end then return a._end>b._end end
  return a.quality>b.quality
end
function M.func(input,env)
  local iterate,state,control=input:iter()
  local function iterator() local candidate=iterate(state,control);control=candidate;return candidate end
  local buffered,chosen={},{}
  for i=1,64 do
    local candidate=iterator()
    if not candidate then break end
    buffered[#buffered+1]=candidate
    local previous=chosen[candidate.text]
    if not previous or preferred(candidate,previous) then chosen[candidate.text]=candidate end
  end
  local native,extra,seen={},{},{}
  for _,candidate in ipairs(buffered) do
    if chosen[candidate.text]==candidate and not seen[candidate.text] then
      seen[candidate.text]=true
      table.insert(cloud(candidate) and extra or native,candidate)
    end
  end
  table.sort(extra,function(a,b) if outranks(a,b) then return true end;if outranks(b,a) then return false end;return a.text<b.text end)
  -- A one-letter input is normally an abbreviation. Imported shortcuts or
  -- syllabic-consonant examples must not freeze its first page above Rime's
  -- own, learnable candidates. Apply this to every letter, not just m.
  local raw=env.engine.context.input
  if raw:match('^[a-z]$') then
    local page=math.max(1,math.min(10,env.engine.schema.config:get_int('menu/page_size') or 5))
    for i=1,math.min(page,#native) do yield(native[i]) end
    for _,candidate in ipairs(extra) do yield(candidate) end
    for i=page+1,#native do yield(native[i]) end
  else
  local position=1
  for _,candidate in ipairs(native) do
    while position<=#extra and outranks(extra[position],candidate) do yield(extra[position]);position=position+1 end
    yield(candidate)
  end
  while position<=#extra do yield(extra[position]);position=position+1 end
  end
  while true do
    local candidate=iterator();if not candidate then break end
    if not seen[candidate.text] then seen[candidate.text]=true;yield(candidate) end
  end
end
return M
