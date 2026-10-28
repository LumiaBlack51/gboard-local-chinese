package local.gboard.enhanced;

import java.util.*;

/** Reorder display positions while leaving every native selection ID intact. */
public final class SharedRanking {
  public static int[] order(String[] words,int[] nativeIds,List<LearningFile.Entry> learned,String preferred){
    if(words.length!=nativeIds.length)throw new IllegalArgumentException("candidate lengths differ");
    int[] unchanged=new int[words.length];for(int i=0;i<unchanged.length;i++)unchanged[i]=i;
    if(learned.isEmpty())return unchanged;
    Map<String,LearningFile.Entry> statistics=new HashMap<>();Map<String,Integer> ranks=new HashMap<>();
    for(int i=0;i<learned.size();i++){LearningFile.Entry e=learned.get(i);if(e.count>0){statistics.put(e.word,e);ranks.put(e.word,6+i);}}
    List<Integer> pool=new ArrayList<>(),extra=new ArrayList<>();
    for(int i=0;i<words.length;i++)(nativeIds[i]>=0||statistics.containsKey(words[i])?pool:extra).add(i);
    pool.sort((a,b)->{
      boolean ap=words[a].equals(preferred),bp=words[b].equals(preferred);if(ap!=bp)return ap?-1:1;
      LearningFile.Entry ae=statistics.get(words[a]),be=statistics.get(words[b]);
      int ar=nativeIds[a]>=0?Math.min(50,nativeIds[a])+1:ranks.get(words[a]);
      int br=nativeIds[b]>=0?Math.min(50,nativeIds[b])+1:ranks.get(words[b]);
      double as=Math.log1p(ae==null?0:ae.count)-0.01*ar,bs=Math.log1p(be==null?0:be.count)-0.01*br;
      if(as!=bs)return Double.compare(bs,as);
      long at=ae==null?0:ae.last,bt=be==null?0:be.last;if(at!=bt)return Long.compare(bt,at);
      return Integer.compare(ar,br);
    });
    List<Integer> result=new ArrayList<>();int lead=Math.min(5,pool.size());
    result.addAll(pool.subList(0,lead));result.addAll(extra);result.addAll(pool.subList(lead,pool.size()));
    int[] order=new int[result.size()];for(int i=0;i<order.length;i++)order[i]=result.get(i);return order;
  }
}
