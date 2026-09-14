# Leading-:: namespace values in bare-call arguments (socket.cr shape —
# verified: foo level: ::Int32 compiles and runs)

getsockopt optname, 0, level: ::Socket::Protocol::TCP

foo level: ::X

foo a, level: ::X, b
