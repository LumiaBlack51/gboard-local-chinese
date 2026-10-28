-- Called only by normal Rime translation. No timer, watcher or network.
local M = {}
local fingerprint, words = nil, {}
local function compact(code)
  return (code:gsub("u:","v"):gsub("ü","v"):gsub("[' ]",''))
end
function M.init(env)
  local dictionary = env.engine.schema.config:get_string('translator/dictionary')
  if dictionary and dictionary ~= '' then env.reverse = ReverseLookup(dictionary) end
  env.native = {}
  env.syllables = {}
  -- Use the same dictionary language as script_translator. A selected cloud
  -- Phrase can then become a normal, learnable user_phrase on the next query.
  env.memory = Memory(env.engine, env.engine.schema)
  env.memory:memorize(function(commit)
    if env.engine.context:get_option('incognito') then return false end
    for _, entry in ipairs(commit:get()) do
      -- Native entries already belong to script_translator's learning path.
      if entry.custom_code ~= '' and #env.memory:decode(entry.code) == 0 then
        env.memory:update_userdict(entry, 1, '')
      end
    end
    return true
  end)
end
function M.fini(env)
  if env.memory.disconnect then env.memory:disconnect() end
  env.memory = nil
  collectgarbage('collect')
end
local function syllable(env, code)
  if env.syllables[code] ~= nil then return env.syllables[code] end
  local found = false
  env.memory:dict_lookup(code, false, 1)
  for entry in env.memory:iter_dict() do
    local decoded = env.memory:decode(entry.code)
    found = #decoded == 1 and decoded[1] == code
    break
  end
  env.syllables[code] = found
  return found
end
local function spelling(env, code, count)
  local memo = {}
  local function split(at, remaining)
    if remaining == 0 then return at > #code and '' or nil end
    local key = at .. ':' .. remaining
    if memo[key] ~= nil then return memo[key] or nil end
    for size = math.min(6, #code-at+1), 1, -1 do
      local part = code:sub(at, at+size-1)
      if syllable(env, part) then
        local tail = split(at+size, remaining-1)
        if tail then memo[key] = part .. ' ' .. tail; return memo[key] end
      end
    end
    memo[key] = false
  end
  return split(1, count)
end
local function native_entry(env, word, code)
  local key = word .. '\t' .. code
  if env.native[key] ~= nil then return env.native[key] end
  local found = false
  if env.reverse then
    local reading = env.reverse:lookup(word)
    local syllables = {}
    for syllable in reading:gmatch('%S+') do syllables[#syllables+1] = syllable end
    local length = utf8.len(word)
    for i=1,#syllables,length do
      if i+length-1<=#syllables and compact(table.concat(syllables,'',i,i+length-1))==code then found=true;break end
    end
  end
  env.native[key] = found
  return found
end
function M.func(input, seg, env)
  if env.engine.context:get_option('incognito') then return end
  local path = rime_api.get_user_data_dir() .. '/cloud-phrases.tsv'
  local file = io.open(path, 'r')
  if not file then return end
  local current = file:read('*l')
  if current ~= fingerprint then
    local next_words = {}
    for line in file:lines() do
      local word, code, weight = line:match('^([^\t]+)\t([a-z]+)\t(%d+)$')
      if word then
        next_words[code] = next_words[code] or {}
        table.insert(next_words[code], {word=word, weight=tonumber(weight)})
      end
    end
    for _, group in pairs(next_words) do table.sort(group,function(a,b) return a.weight==b.weight and a.word<b.word or a.weight>b.weight end) end
    words, fingerprint = next_words, current
  end
  file:close()
  local code = compact(input)
  local found, emitted = words[code] or {}, 0
  for _, row in ipairs(found) do
    -- Keep the real native Phrase for known readings: it carries Rime's
    -- dictionary identity and selection learning. Generic Lua candidates do not.
    if not native_entry(env,row.word,code) then
      local spelled = spelling(env, code, utf8.len(row.word))
      local candidate
      if spelled then
        local entry = DictEntry()
        entry.text, entry.custom_code = row.word, spelled
        candidate = Phrase(env.memory, 'cloud_phrase', seg.start, seg._end, entry):toCandidate()
      else
        candidate = Candidate('cloud_phrase',seg.start,seg._end,row.word,'')
      end
      candidate.preedit=input
      -- Bring the bounded cloud stream into the merge filter's lookahead.
      -- The filter removes this transport offset before placing candidates.
      candidate.quality=10.9+0.09*math.log(1+row.weight)/math.log(1000001)
      yield(candidate)
      emitted=emitted+1;if emitted>=20 then break end
    end
  end
end
return M
