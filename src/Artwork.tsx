import React from 'react';
import {StyleSheet, View} from 'react-native';

export function BrandMark() {
  return <View accessible={false} style={s.mark}><View style={s.markScreen}/><View style={s.markFinger}/><View style={s.markDot}/></View>;
}

export function KioskArtwork() {
  return <View accessible={false} style={s.art}>
    <View style={s.halo}/><View style={s.spark}/><View style={s.sparkSmall}/>
    <View style={s.stand}/><View style={s.base}/>
    <View style={s.kiosk}><View style={s.cameraDot}/><View style={s.screen}>
      <View style={s.screenLine}/><View style={s.tiles}>{[0, 1, 2, 3].map(n => <View key={n} style={[s.tile, n === 2 && s.selected]}/>)}</View>
      <View style={s.screenButton}/>
    </View></View>
    <View style={s.hand}><View style={s.finger}/><View style={s.palm}/><View style={s.cuff}/></View>
    <View style={s.touchRing}/>
  </View>;
}
const s = StyleSheet.create({
  mark: {width: 40, height: 40, borderRadius: 13, backgroundColor: '#176657', overflow: 'hidden'},
  markScreen: {position: 'absolute', left: 10, top: 9, width: 20, height: 24, borderWidth: 2, borderColor: '#ebfaf1', borderRadius: 5},
  markFinger: {position: 'absolute', right: 8, bottom: -4, width: 9, height: 23, backgroundColor: '#e4b594', borderRadius: 6, transform: [{rotate: '-30deg'}]}, markDot: {position: 'absolute', top: 15, left: 15, width: 5, height: 5, borderRadius: 2, backgroundColor: '#a6d8c7'},
  art: {width: 126, height: 140}, halo: {position: 'absolute', top: 8, left: 1, width: 122, height: 122, backgroundColor: '#c7e3d6', borderRadius: 61},
  spark: {position: 'absolute', top: 9, right: 7, width: 9, height: 9, backgroundColor: '#da9c72', borderRadius: 3, transform: [{rotate: '35deg'}]},
  sparkSmall: {position: 'absolute', top: 34, left: 3, width: 5, height: 5, backgroundColor: '#176657', borderRadius: 3},
  stand: {position: 'absolute', bottom: 16, left: 45, width: 30, height: 41, borderRadius: 5, backgroundColor: '#9cbcaf'}, base: {position: 'absolute', bottom: 8, left: 26, width: 68, height: 10, backgroundColor: '#789e8f', borderRadius: 5},
  kiosk: {position: 'absolute', top: 16, left: 20, width: 79, height: 103, backgroundColor: '#245d4f', borderRadius: 12, padding: 7}, cameraDot: {height: 3, width: 3, borderRadius: 2, backgroundColor: '#8ac7b1', alignSelf: 'center', marginBottom: 5},
  screen: {flex: 1, borderRadius: 6, backgroundColor: '#fcfdf8', padding: 7, gap: 5}, screenLine: {width: 32, height: 4, borderRadius: 2, backgroundColor: '#9aada4'}, tiles: {flexDirection: 'row', flexWrap: 'wrap', gap: 4}, tile: {width: 21, height: 19, borderRadius: 4, backgroundColor: '#e6eae2'}, selected: {backgroundColor: '#71b199', borderWidth: 1, borderColor: '#176657'}, screenButton: {height: 7, marginTop: 2, borderRadius: 3, backgroundColor: '#176657'},
  hand: {position: 'absolute', bottom: 1, right: 6, width: 43, height: 72, transform: [{rotate: '-26deg'}]}, finger: {position: 'absolute', top: 0, left: 5, width: 12, height: 40, borderRadius: 7, backgroundColor: '#e4b594'}, palm: {position: 'absolute', top: 27, left: 5, width: 33, height: 33, borderRadius: 12, backgroundColor: '#e4b594'}, cuff: {position: 'absolute', bottom: 0, left: 3, width: 34, height: 19, borderRadius: 5, backgroundColor: '#d68460'}, touchRing: {position: 'absolute', left: 55, top: 63, width: 21, height: 21, borderRadius: 11, borderWidth: 2, borderColor: '#176657'},
});
