-- Aggregate actual selections only. No per-keystroke history or network work.
local M = {}
local local_codes, cloud_path, fingerprint, cloud = {}, nil, nil, {}
local history_path,history_fingerprint,history=nil,nil,{}

function M.compact(code)
  return (code:gsub('u:','v'):gsub('ü','v'):gsub("[' ’]",''))
end

local function valid_word(word)
  local size = utf8.len(word)
  if not size or size < 1 or size > 32 then return false end
  for _, c in utf8.codes(word) do
    if not (c==0x3007 or (c>=0x3400 and c<=0x9fff) or (c>=0xf900 and c<=0xfaff)
      or (c>=0x20000 and c<=0x323af)) then return false end
  end
  return true
end

local function root() return rime_api.get_user_data_dir() end
local function read_rows(file, groups)
  for line in file:lines() do
    local word,code,count,last=line:match('^([^\t]+)\t([a-z]+)\t(%d+)\t(%d+)$')
    if word then
      count,last=tonumber(count),tonumber(last)
      if count>0 and count<=1000000 and #code<=128 and valid_word(word) then
        groups[code]=groups[code] or {}
        groups[code][word]={count=count,last=last}
      end
    end
  end
end

local function local_group(code)
  local path=root()..'/selection-learning/'..code..'.tsv'
  if not local_codes[path] then
    local groups={}
    local file=io.open(path,'r') or io.open(path..'.new','r') or io.open(path..'.old','r')
    if file then read_rows(file,groups);file:close() end
    local_codes[path]=groups[code] or {}
  end
  return local_codes[path],path
end

local function shared()
  local path=root()..'/cloud-learning.tsv'
  local file=io.open(path,'r')
  if not file then return {} end
  local current=file:read('*l')
  if path~=cloud_path or current~=fingerprint then
    local next_cloud={};read_rows(file,next_cloud)
    cloud_path,fingerprint,cloud=path,current,next_cloud
  end
  file:close()
  return cloud
end

local function historical()
  local path=root()..'/local-native-learning.tsv'
  local file=io.open(path,'r');if not file then return {} end
  local current=file:read('*l')
  if path~=history_path or current~=history_fingerprint then
    local rows={};read_rows(file,rows);history_path,history_fingerprint,history=path,current,rows
  end
  file:close();return history
end

function M.ranking(raw)
  local code=M.compact(raw)
  if #code>128 or not code:match('^[a-z]+$') then return {},nil end
  local own=local_group(code)
  local merged,latest_remote,latest_local,preferred={},0,0,nil
  for word,row in pairs(shared()[code] or {}) do
    merged[word]={count=row.count,last=row.last}
    latest_remote=math.max(latest_remote,row.last)
  end
  for word,row in pairs(own) do
    local previous=merged[word] or {count=0,last=0}
    merged[word]={count=math.max(previous.count,row.count),last=math.max(previous.last,row.last)}
    if row.last>latest_local then latest_local,preferred=row.last,word end
  end
  -- Older remote totals cannot pin a word above a newer explicit local choice.
  if latest_local==0 or latest_local<latest_remote then preferred=nil end
  return merged,preferred
end

function M.record(raw,word,native_count)
  local code=M.compact(raw)
  if #code>128 or not code:match('^[a-z]+$') or not valid_word(word) then return end
  local group,path=local_group(code)
  local previous=group[word] or {count=0,last=0}
  local baseline=(historical()[code] or {})[word]
  local latest=0
  for _,row in pairs(group) do latest=math.max(latest,row.last) end
  for _,row in pairs(shared()[code] or {}) do latest=math.max(latest,row.last) end
  local now=os.time()*1000
  group[word]={count=math.min(1000000,math.max(previous.count+1,(native_count or 0)+1,(baseline and baseline.count or 0)+1)),
    last=math.max(now,math.min(now+300000,latest+1))}
  local words={};for text in pairs(group) do words[#words+1]=text end;table.sort(words)
  -- Small per-input aggregate files avoid rewriting the whole user's profile.
  local file=io.open(path..'.new','w')
  if not file then return end
  file:write('# Local selection aggregates:1\n')
  for _,text in ipairs(words) do
    local row=group[text]
    file:write(text,'\t',code,'\t',string.format('%.0f',row.count),'\t',string.format('%.0f',row.last),'\n')
  end
  file:close()
  if not os.rename(path..'.new',path) then
    -- Windows CRT rename does not replace an existing destination. Keep a
    -- recoverable aggregate across both renames; never append selection events.
    if package.config:sub(1,1)=='\\' then
      os.remove(path..'.old')
      local moved=os.rename(path,path..'.old')
      if os.rename(path..'.new',path) then
        os.remove(path..'.old')
      elseif moved then
        os.rename(path..'.old',path)
      end
    else os.remove(path..'.new') end
  end
end

function M.committed(ctx)
  if ctx:get_option('incognito') then return end
  local word=ctx:get_commit_text()
  local candidate=ctx:get_selected_candidate()
  local native_count=0
  if candidate and candidate.text==word then
    local ok,count=pcall(function() return candidate:get_genuine().entry.commit_count end)
    if ok and count then native_count=math.max(0,count) end
  end
  M.record(ctx.input,word,native_count)
end

return M
