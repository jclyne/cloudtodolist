
from google.appengine.api import channel

# List of connected channel clients
client_list = {}

def parse_client_id(client_id):
    return client_id.split("_")

def generate_token(client_id):
    return channel.create_channel(client_id)
    
def add_update_client(client_id):
    """ Adds a client id to the client list """

    user_id=parse_client_id(client_id)[0]

    try:
        client_list[user_id].add(client_id)
    except KeyError:
        client_list[user_id] = set( (client_id,) )

def remove_update_client(client_id):
    """ Removes a client id to the client list """

    user_id=parse_client_id(client_id)[0]
    try:
        client_list[user_id].remove(client_id)
    except KeyError:
        pass

def send_update(user_id,message):
    """ Sends the specfied updated message to all clients in the client_list """

    try:
        for client_id in client_list[user_id]:
            channel.send_message(client_id,message)
    except KeyError:
        pass
 