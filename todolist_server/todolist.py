""" Basic todo list using webpy 0.3 """
import web
import json
from todolistdb import TodoListDb


### Url mappings

urls = (
    '/todolist', 'Index',
    '/todolist/api/getentry', 'GetEntry' ,
    '/todolist/api/setentry', 'SetEntry',
    '/todolist/api/delentry', 'DelEntry'
)


class Index:
    def GET(self):
        raise web.seeother("static/todolist.html")

class ServletBase:

    db=TodoListDb (web.database(
            dbn='mysql',
            db='todolist_example',
            user='root',
            passwd="Vel0city")
    )

    @staticmethod
    def parse_id_parameters(data):
        if data.has_key("id"):
            try:
                return tuple([int(id) for id in data["id"].split()])
            except ValueError:
                return ()
        else:
            return None

    @ staticmethod
    def to_json(data):
        web.header('Content-Type', 'application/json')
        return json.dumps(data,indent=2)

class GetEntry(ServletBase):

    def GET(self):
        """ Show page """
        data = web.input()
        entries = self.db.get_entry(self.parse_id_parameters(data))
        return self.to_json(entries)

class SetEntry(ServletBase):

    def PUT(self):
        data = dict(web.input())
        new_entry=self.db.set_entry(**data)
        return self.to_json(new_entry)

class DelEntry(ServletBase):

    def PUT(self):
        data = web.input()
        id_list= self.parse_id_parameters(data)
        deleted_entries=()
        if id_list:
            deleted_entries=self.db.delete_entry(self.parse_id_parameters(data))

        return self.to_json(deleted_entries)


app = web.application(urls, globals())

if __name__ == '__main__':
    app.run()
  