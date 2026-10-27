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
      local candidate=Candidate('cloud_phrase',seg.start,seg._end,row.word,'')
      candidate.preedit=input
      candidate.quality=10+math.log(1+row.weight)
      yield(candidate)
      emitted=emitted+1;if emitted>=20 then break end
    end
  end
end
return M
