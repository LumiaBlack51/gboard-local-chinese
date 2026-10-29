"""Exercise the actual Lua compact index, including exact-prefix collisions."""
import ctypes as C,ctypes.util,unittest
from pathlib import Path

class CompactSnapshotTest(unittest.TestCase):
 def test_large_snapshot_and_exact_codes(self):
  library=ctypes.util.find_library('lua5.4')
  if not library:self.skipTest('Lua 5.4 shared runtime required')
  lua=C.CDLL(library);lua.luaL_newstate.restype=C.c_void_p;state=lua.luaL_newstate()
  lua.luaL_openlibs.argtypes=[C.c_void_p];lua.luaL_loadstring.argtypes=[C.c_void_p,C.c_char_p];lua.lua_pcallk.argtypes=[C.c_void_p,C.c_int,C.c_int,C.c_int,C.c_longlong,C.c_void_p];lua.lua_tolstring.argtypes=[C.c_void_p,C.c_int,C.POINTER(C.c_size_t)];lua.lua_tolstring.restype=C.c_char_p;lua.lua_close.argtypes=[C.c_void_p]
  root=Path(__file__).resolve().parents[1];path=(root/'sync/compact_tsv.lua').as_posix().replace("'","\\'")
  script=f"local index=dofile('{path}')\n"+r'''
local source=assert(io.tmpfile())
source:write('a\tfirst\naa\tsecond\nba\tthird\na\tfourth\n')
for n=1,20000 do source:write('zhong'..string.rep('a',n%12+1)..'\tword'..n..'\n') end
source:seek('set',0)
local groups=index.read(source,function(line) return line:match('^([a-z]+)\t(.+)$') end)
source:close()
local count=0
for _,value in pairs(groups) do assert(type(value)=='string');count=count+1 end
assert(count==4,'index grew per word rather than per prefix')
local function values(code)
 local out={};for value in index.rows(groups,code) do out[#out+1]=value end;return table.concat(out,'|'),#out
end
assert(values('a')=='first|fourth','single-letter prefix collision')
assert(values('aa')=='second','two-letter exact code')
assert(values('ba')=='third','bucket prefix collision')
assert(values('notfound')=='','unknown code returned a row')
local _,matches=values('zhonga');assert(matches==math.floor(20000/12),'long prefix collision')
assert(values('zhong')=='','shorter absent prefix matched longer words')
'''
  # Keep Python's source readable while passing actual TSV escapes to Lua.
  script=script.replace('\\\\t','\\t').replace('\\\\n','\\n')
  try:
   lua.luaL_openlibs(state)
   status=lua.luaL_loadstring(state,script.encode())
   if status==0:status=lua.lua_pcallk(state,0,0,0,0,None)
   self.assertEqual(status,0,(lua.lua_tolstring(state,-1,None) or b'').decode())
  finally:lua.lua_close(state)

if __name__=='__main__':unittest.main()
