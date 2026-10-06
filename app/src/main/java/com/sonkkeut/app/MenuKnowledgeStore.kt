package com.sonkkeut.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kr.sonkkeut.android.MenuDocument
import org.json.JSONArray

data class MenuKnowledge(val terms: List<String>,val description: String)

/** User-authored search hints, isolated by backend and store. Never ingredient/allergen facts. */
class MenuKnowledgeStore(context: Context): SQLiteOpenHelper(context,"menu-knowledge.db",null,1) {
    override fun onCreate(db: SQLiteDatabase) { db.execSQL("CREATE TABLE knowledge (scope TEXT NOT NULL, name TEXT NOT NULL, terms TEXT NOT NULL, description TEXT NOT NULL, PRIMARY KEY(scope,name))") }
    override fun onUpgrade(db: SQLiteDatabase,oldVersion: Int,newVersion: Int) = Unit
    private fun scope(server: String,store: String)=JSONArray(listOf(server,store)).toString()
    fun get(server: String,store: String,name: String): MenuKnowledge? = readableDatabase.query("knowledge",arrayOf("terms","description"),"scope=? AND name=?",arrayOf(scope(server,store),name),null,null,null).use { c ->
        if(!c.moveToFirst()) null else { val a=JSONArray(c.getString(0)); MenuKnowledge((0 until a.length()).map { a.getString(it) },c.getString(1)) }
    }
    fun save(server: String,store: String,name: String,terms: List<String>,description: String) {
        require(name.isNotBlank() && name.length<=80)
        require(terms.size<=30 && terms.all { it.isNotBlank() && it.length<=80 } && description.length<=500)
        val row=writableDatabase.insertWithOnConflict("knowledge",null,ContentValues().apply {
            put("scope",scope(server,store)); put("name",name); put("terms",JSONArray(terms.distinct()).toString()); put("description",description)
        },SQLiteDatabase.CONFLICT_REPLACE)
        check(row!=-1L) { "Search information could not be saved" }
    }
    fun remove(server: String,store: String,name: String) { writableDatabase.delete("knowledge","scope=? AND name=?",arrayOf(scope(server,store),name)) }
    fun enrich(server: String,store: String,menu: List<MenuDocument>)=menu.map { item ->
        val local=get(server,store,item.name)
        if(local==null) item else item.copy(relatedTerms=(item.relatedTerms+local.terms).distinct(),description=listOf(item.description,local.description).filter { it.isNotBlank() }.joinToString(" "))
    }
}
