"""Real librime ABI harness with an isolated user/database directory; no UI events."""
import ctypes as C
import ctypes.util
from pathlib import Path
import shutil
import tempfile
import yaml

class Traits(C.Structure):
    _fields_=[('data_size',C.c_int)]+[(x,C.c_char_p) for x in ['shared_data_dir','user_data_dir','distribution_name','distribution_code_name','distribution_version','app_name']]+[('modules',C.POINTER(C.c_char_p)),('min_log_level',C.c_int)]+[(x,C.c_char_p) for x in ['log_dir','prebuilt_data_dir','staging_dir']]
class Candidate(C.Structure):
    _fields_=[('text',C.c_char_p),('comment',C.c_char_p),('reserved',C.c_void_p)]
class Iterator(C.Structure):
    _fields_=[('ptr',C.c_void_p),('index',C.c_int),('candidate',Candidate)]
class Commit(C.Structure):
    _fields_=[('data_size',C.c_int),('text',C.c_char_p)]

class RimeHost:
    def __init__(self,live=None,learning=False,configure=None):
        self.live=live or Path.home()/'.local/share/fcitx5/rime'
        self.temp=tempfile.TemporaryDirectory(prefix='rime-isolated-check-')
        self.user=Path(self.temp.name);build=self.user/'build';build.mkdir()
        shutil.copytree(self.live/'lua',self.user/'lua')
        for source in (self.live/'build').iterdir():
            if source.is_file():
                if source.suffix=='.yaml':shutil.copyfile(source,build/source.name)
                else:(build/source.name).symlink_to(source)
        for name in ['cn_dicts','en_dicts','opencc','others']:
            source=self.live/name
            if source.exists():(self.user/name).symlink_to(source,target_is_directory=True)
        for source in self.live.glob('*.txt'):
            shutil.copyfile(source,self.user/source.name)
        shutil.copyfile(self.live/'cloud-phrases.tsv',self.user/'cloud-phrases.tsv')
        schema=build/'rime_ice.schema.yaml';config=yaml.safe_load(schema.read_text())
        config.setdefault('translator',{})['enable_user_dict']=learning
        if configure:configure(self.user,config)
        config['engine']['filters'].append('lua_filter@*verify_candidate_source')
        (self.user/'lua/verify_candidate_source.lua').write_text("return {func=function(t,e) for c in t:iter() do c.comment='VERIFY:'..c.type..'|'..c.start..'|'..c._end..'|'..c.preedit..'|'..c.quality yield(c) end end}\n")
        schema.write_text(yaml.safe_dump(config,allow_unicode=True))
        self.r=C.CDLL(ctypes.util.find_library('rime'),mode=C.RTLD_GLOBAL)
        functions=[('Setup',[C.POINTER(Traits)],None),('Initialize',[C.POINTER(Traits)],None),('CreateSession',[],C.c_size_t),('SelectSchema',[C.c_size_t,C.c_char_p],C.c_int),('SetOption',[C.c_size_t,C.c_char_p,C.c_int],None),('ProcessKey',[C.c_size_t,C.c_int,C.c_int],C.c_int),('ClearComposition',[C.c_size_t],None),('CandidateListBegin',[C.c_size_t,C.POINTER(Iterator)],C.c_int),('CandidateListNext',[C.POINTER(Iterator)],C.c_int),('CandidateListEnd',[C.POINTER(Iterator)],None),('SelectCandidate',[C.c_size_t,C.c_size_t],C.c_int),('CommitComposition',[C.c_size_t],C.c_int),('GetCommit',[C.c_size_t,C.POINTER(Commit)],C.c_int),('FreeCommit',[C.POINTER(Commit)],C.c_int),('DestroySession',[C.c_size_t],C.c_int),('Finalize',[],None)]
        for name,args,ret in functions:
            f=getattr(self.r,'Rime'+name);f.argtypes=args;f.restype=ret
        traits=Traits();traits.data_size=C.sizeof(Traits)-C.sizeof(C.c_int)
        traits.shared_data_dir=str(self.user).encode();traits.user_data_dir=str(self.user).encode()
        traits.distribution_name=b'Isolated verification';traits.distribution_code_name=b'verification';traits.distribution_version=b'1';traits.app_name=b'rime.verification';traits.min_log_level=2;traits.log_dir=b'';traits.prebuilt_data_dir=str(build).encode();traits.staging_dir=str(build).encode()
        self.traits=traits;self.r.RimeSetup(C.byref(traits));self.r.RimeInitialize(C.byref(traits))
        self.session=self.r.RimeCreateSession();assert self.session
        assert self.r.RimeSelectSchema(self.session,b'rime_ice')
        self.r.RimeSetOption(self.session,b'ascii_mode',0);self.r.RimeSetOption(self.session,b'incognito',not learning)
    def candidates(self,code,limit=100,details=False):
        pending=Commit();pending.data_size=C.sizeof(Commit)-C.sizeof(C.c_int)
        if self.r.RimeGetCommit(self.session,C.byref(pending)):self.r.RimeFreeCommit(C.byref(pending))
        self.r.RimeClearComposition(self.session)
        for ch in code:self.r.RimeProcessKey(self.session,ord(ch),0)
        iterator=Iterator();out=[]
        if self.r.RimeCandidateListBegin(self.session,C.byref(iterator)):
            while len(out)<limit and self.r.RimeCandidateListNext(C.byref(iterator)):
                out.append(((iterator.candidate.text or b'').decode(),(iterator.candidate.comment or b'').decode().removeprefix('VERIFY:') if details else (iterator.candidate.comment or b'').decode().removeprefix('VERIFY:').split('|')[0]))
            self.r.RimeCandidateListEnd(C.byref(iterator))
        return out
    def select(self,index):
        assert self.r.RimeSelectCandidate(self.session,index)
        self.r.RimeCommitComposition(self.session)
        result=Commit();result.data_size=C.sizeof(Commit)-C.sizeof(C.c_int)
        if self.r.RimeGetCommit(self.session,C.byref(result)):
            text=(result.text or b'').decode();self.r.RimeFreeCommit(C.byref(result));return text
        return ''
    def restart(self):
        """Reopen this isolated database, exercising actual persisted learning."""
        self.r.RimeDestroySession(self.session);self.r.RimeFinalize()
        self.r.RimeInitialize(C.byref(self.traits))
        self.session=self.r.RimeCreateSession();assert self.session
        assert self.r.RimeSelectSchema(self.session,b'rime_ice')
        self.r.RimeSetOption(self.session,b'ascii_mode',0)
    def close(self):
        self.r.RimeDestroySession(self.session);self.r.RimeFinalize();self.temp.cleanup()
    def __enter__(self):return self
    def __exit__(self,*_):self.close()
