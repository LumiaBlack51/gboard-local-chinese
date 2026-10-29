"""Read a private Microsoft Pinyin UDL snapshot; never modify the live IME.

Layout and syllable index reference: studyzy/imewlconverter,
Win10MsPinyinSelfStudyImporter (blob a71b914). Unknown fields are not counts.
"""
import argparse
from collections import Counter
import json
from pathlib import Path
import struct
from common import encode, valid_word

PINYIN = "a ai an ang ao ba bai ban bang bao bei ben beng bi bian biao bie bin bing bo bu ca cai can cang cao ce cen ceng cha chai chan chang chao che chen cheng chi chong chou chu chua chuai chuan chuang chui chun chuo ci cong cou cu cuan cui cun cuo da dai dan dang dao de dei den deng di dia dian diao die ding diu dong dou du duan dui dun duo e ei en eng er fa fan fang fei fen feng fiao fo fou fu ga gai gan gang gao ge gei gen geng gong gou gu gua guai guan guang gui gun guo ha hai han hang hao he hei hen heng hong hou hu hua huai huan huang hui hun huo ji jia jian jiang jiao jie jin jing jiong jiu ju juan jue jun ka kai kan kang kao ke kei ken keng kong kou ku kua kuai kuan kuang kui kun kuo la lai lan lang lao le lei leng li lia lian liang liao lie lin ling liu lo long lou lu luan lue lun luo lv ma mai man mang mao me mei men meng mi mian miao mie min ming miu mo mou mu na nai nan nang nao ne nei nen neng ni nian niang niao nie nin ning niu nong nou nu nuan nue nun nuo nv o ou pa pai pan pang pao pei pen peng pi pian piao pie pin ping po pou pu qi qia qian qiang qiao qie qin qing qiong qiu qu quan que qun ran rang rao re ren reng ri rong rou ru rua ruan rui run ruo sa sai san sang sao se sen seng sha shai shan shang shao she shei shen sheng shi shou shu shua shuai shuan shuang shui shun shuo si song sou su suan sui sun suo ta tai tan tang tao te tei teng ti tian tiao tie ting tong tou tu tuan tui tun tuo wa wai wan wang wei wen weng wo wu xi xia xian xiang xiao xie xin xing xiong xiu xu xuan xue xun ya yan yang yao ye yi yin ying yo yong you yu yuan yue yun za zai zan zang zao ze zei zen zeng zha zhai zhan zhang zhao zhe zhei zhen zheng zhi zhong zhou zhu zhua zhuai zhuan zhuang zhui zhun zhuo zi zong zou zu zuan zui zun zuo".split()
MAGIC = bytes.fromhex('55aa88810200600055aa55aa')

def extract(raw):
    if len(raw) < 0x2400 or raw[:12] != MAGIC:
        raise ValueError('Unsupported Microsoft Pinyin self-learning dictionary')
    count, = struct.unpack_from('<I', raw, 12)
    if count > 100000:
        raise ValueError('Invalid dictionary record count')
    result, stats = {}, Counter(declared=count)
    for index in range(count):
        start = 0x2400 + index * 60
        if start + 60 > len(raw):
            stats['truncated'] += count - index
            break
        length = raw[start+10]
        if not 1 <= length <= 12:
            stats['invalid_length'] += 1
            continue
        try:
            word = raw[start+12:start+12+length*2].decode('utf-16-le', errors='strict')
        except UnicodeError:
            stats['invalid_unicode'] += 1
            continue
        if len(word) != length or not valid_word(word):
            stats['excluded_non_chinese'] += 1
            continue
        indices = struct.unpack_from('<'+'H'*length, raw, start+12+length*2)
        if any(i >= len(PINYIN) for i in indices):
            stats['invalid_pinyin'] += 1
            continue
        # An import weight is deliberately independent of real selection counts.
        result[(word, ''.join(PINYIN[i] for i in indices))] = 100
        stats['accepted_records'] += 1
    stats['unique_words'] = len(result)
    return result, dict(stats)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('snapshot', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    words, report = extract(args.snapshot.read_bytes())
    args.output.write_bytes(encode(words))
    print(json.dumps(report, ensure_ascii=False))
