# A Websocket Demo

This project shows how to use the new websocket support in Fulcro 2.2.0+

## Setting Up

The shadow-cljs compiler uses all cljsjs and NPM js dependencies through
NPM. If you use a library that is in cljsjs you will also have to add
it to your `package.json`.

You also cannot compile this project until you install the ones it
depends on already:

```
$ npm install
```

or if you prefer `yarn`:

```
$ yarn install
```

Adding NPM Javascript libraries is as simple as adding them to your
`package.json` file and requiring them! See the
[the Shadow-cljs User's Guide](https://shadow-cljs.github.io/docs/UsersGuide.html#_javascript)
for more information.

## Development Mode

Shadow-cljs handles the client-side development build. The file
`src/main/wsfix/client.cljs` contains the code to start and refresh
the client for hot code reload.

Running client development builds:

```
$ npx shadow-cljs watch main
```

The compiler will detect which builds are affected by a change and will minimize
incremental build time.

### The Server

Start a clj REPL in IntelliJ, or from the command line:

```bash
$ lein repl
user=> (go)
...
user=> (restart) ; stop, reload server code, and go again
user=> (tools-ns/refresh) ; retry code reload if hot server reload fails
```

The URL to work on your application is then
[http://localhost:3000](http://localhost:3000).

Hot code reload, preloads, and such are all coded into the javascript,
so serving the files from the alternate server is fine.


## Files of interest

- `wsfix.server` - Samples of a hand-build and easy server.
- `wsfix.api.mutations` - Sample mutations. One that is slow, and one that triggers an error. Both
useful for playing with error recovery.
- `client` - Sample client networking setup
