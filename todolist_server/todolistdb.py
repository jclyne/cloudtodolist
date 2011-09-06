import _mysql_exceptions
import web

class TodoListDb(object):

    def __init__(self,db):
        self.db = db

    @staticmethod
    def __build_id_where_clause(id_list):
        return " or ".join("id=%s"%id for id in id_list )

    def __call_func(self,func):
        return self.db.query("SELECT %s"%func)[0][func]

    def get_entry(self,id_list=None):
        where=None
        if id_list:
            where= self.__build_id_where_clause(id_list)
        return tuple( self.db.select('entries',  where=where, order='id') )

    def set_entry(self,id=None,title=None,notes=None,complete=None,**kwargs):

        vars={}
        if id: vars['id']=id
        if title: vars['title']=title
        if notes: vars['notes']=notes
        if complete: vars['complete']=complete

        print vars
        if not vars \
          or (len(vars) == 1 and vars.has_key('id'))\
          or not (id or title):
            raise web.badrequest()

        query="""INSERT
                INTO entries ({0})
                VALUES ({1})
                ON DUPLICATE KEY UPDATE {2};
                """.format(",".join(vars.keys()),
                           ",".join(["$"+key for key in vars.keys()]),
                           ",".join([key+"=$"+key for key in vars.keys() if key != 'id'])
        )

        try:
            self.db.query(query,vars)
        except _mysql_exceptions.OperationalError,e:
            raise web.badrequest()

        if not id:
            id = self.__call_func("last_insert_id()")

        return self.get_entry(id,)

    def delete_entry(self,id_list):
        where=self.__build_id_where_clause(id_list)
        res=[res["id"] for res in self.db.query("SELECT id from entries WHERE %s"%where)]
        self.db.delete('entries', where=where)
        return res