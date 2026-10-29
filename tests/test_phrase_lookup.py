"""Execute the actual Lua snapshot reader with exact codes and UTF-8 boundaries."""
import ctypes as C,ctypes.util,unittest,tempfile
from pathlib import Path
class SnapshotLookupTest(unittest.TestCase):
 def test_large_snapshot_boundaries_and_refresh(self):
  library=ctypes.util.find_library('lua5.4')
  if not library:self.skipTest('Lua 5.4 shared runtime required')
  lua=C.CDLL(library);lua.luaL_newstate.restype=C.c_void_p;state=lua.luaL_newstate()
  lua.luaL_openlibs.argtypes=[C.c_void_p];lua.luaL_loadstring.argtypes=[C.c_void_p,C.c_char_p];lua.lua_pcallk.argtypes=[C.c_void_p,C.c_int,C.c_int,C.c_int,C.c_longlong,C.c_void_p];lua.lua_tolstring.argtypes=[C.c_void_p,C.c_int,C.POINTER(C.c_size_t)];lua.lua_tolstring.restype=C.c_char_p;lua.lua_close.argtypes=[C.c_void_p]
  source=(Path(__file__).resolve().parents[1]/'sync/phrase_lookup.lua').as_posix().replace("'","\\'")
  with tempfile.TemporaryDirectory(prefix='snapshot-lookup-') as folder:
   path=folder.replace("'","\\'")
   script=f"rime_api={{get_user_data_dir=function() return '{path}' end}};local snapshots=dofile('{source}')\n"+r'''
local path=rime_api.get_user_data_dir()..'/fixture.tsv'
local source=assert(io.open(path,'w'))
source:write('# snapshot 1\n甲\ta\tfirst\n乙\taa\tsecond\n丙\tba\tthird\n丁\ta\tfourth\n')
for n=1,20000 do source:write('测试'..utf8.char(0x4e00+n%20000)..'\tzhong'..string.rep('a',n%12+1)..'\t'..n..'\n') end
local long=string.rep(utf8.char(0x20000),32)
source:write(long..'\tlong\t128\n')
source:write(long..'甲\ttooheavy\t131\n# ignored\tcomment\t123\n');source:close()
local function values(code)
 local out={};local iterator=snapshots.iter('fixture.tsv',code)
 if iterator then for word,value in iterator do out[#out+1]=word..':'..value end end
 return table.concat(out,'|'),#out
end
assert(values('a')=='甲:first|丁:fourth','single-letter prefix collision')
assert(values('aa')=='乙:second','two-letter exact code')
assert(values('ba')=='丙:third','bucket prefix collision')
assert(values('notfound')=='','unknown code returned a row')
local _,count=values('zhonga');assert(count==math.floor(20000/12),'long code prefix collision')
assert(values('zhong')=='','short absent code matched longer words')
assert(values('long')==long..':128','128-byte word boundary')
assert(values('tooheavy')=='','truncated oversized word returned')
assert(values('comment')=='','comment returned as a word')
source=assert(io.open(path,'w'));source:write('# snapshot 2\n值\ta\tnew\n');source:close()
assert(values('a')=='值:new','same-session snapshot refresh failed')
assert(snapshots.iter('missing.tsv','a')==nil,'missing-file fallback')
'''
   script=script.replace('\\\\t','\\t').replace('\\\\n','\\n')
   try:
    lua.luaL_openlibs(state);status=lua.luaL_loadstring(state,script.encode())
    if status==0:status=lua.lua_pcallk(state,0,0,0,0,None)
    self.assertEqual(status,0,(lua.lua_tolstring(state,-1,None) or b'').decode())
   finally:lua.lua_close(state)
if __name__=='__main__':unittest.main()
