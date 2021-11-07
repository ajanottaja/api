# Ajanottaja - A simple time keeping tool

This is the backend API of the [Ajanottaja time keeping tool](https://ajanottaja.app).
It is implemented in the [Clojure](https://clojure.org/) programming language using [Postgresql](https://www.postgresql.org/) as its data layer.
[Auth0](https://auth0.com/) is used for user registration and authentication.

## Development

Requirements:

- Postgresql database and user
- Auth0 tenant

```
# Clone the repository
git clone git@github.com:ajanottaja/api.git

# Go to the root of the project
cd api

# Setup the config file (edit values appropriate for your dev environment)
cp resources/config.example.edn resources/config.edn

# Set up a .secrets.edn file
cat << EOF > .secrets.edn
{:db {:password "your-db-password"}
 :server {:api-token "your-api-token"}}
EOF
```

The `api-token` in the `.secrets.edn` secrets file is used to allow Auth0 access to protected API routes.
You can generate any password or passphrase you like to use as an api-token.


### Auth0 Flow and Custom Actions

Ajanottaja uses [Auth0 Custom Actions](https://auth0.com/docs/actions) to handle user registration and login.
On new Auth0 user registrations a user record will be created in the DB to act as a proxy for the auth0 user.
On logins the Ajanottaja user id will be inserted as a custom claim in the Auth0 JWT.
This allows the API to use the Ajanottaja specific user id when authentication the user and performing data queries.

The `resources/auth0-actions` folder contains the scripts that need to be registered.
Add the `create-user.js` contents as an action in the post registration flow.
Add the `login.js` contents as an action in the login flow.

For both actions you should insert the above `api-token` as an `API_TOKEN` secret in the action editor.
This will grant Auth0 action to the relevant `/auth-zero` API endpoints in Ajanottaja.

In the future Auth0 action and flow setup might be automated to reduce setup complexity.


### Start developing

The project uses Clojure tools deps to handle dependencies, builds, etc.
To start development launch a Clojure REPL adding the `clj` and `reveal` alias using your prefered dev environment (e.g. cider's jack-in).

You can also start a REPL from the command line:

```bash
clj -Sdeps '{:deps {nrepl/nrepl {:mvn/version,"0.8.3"},cider/cider-nrepl {:mvn/version,"0.26.0"}}}' -A:clj:reveal
```

## Building for prod

You can build a runnable JAR using the provided build script:

```bash
# Build
./uberdeps/package.sh

# Then, granted config is in order, run
java -Dmalli.registry/type=custom -jar ./target/ajanottaja.jar
```

Alternatively build a Docker image using:

```
# Build
docker build -t myname/ajanottaja:latest .
```


# License

Licensed under AGPLv3, see [License](/LICENSE).


In short this means you can:

1. Copy, run, and redistribute the code for free
2. Modify the code and run a public service, in which case source code MUST be released
3. Modify the code and run a private service (e.g. for yourself or your family) without releasing source code, but it is nice if you do
4. You must retain the copyright in any modified work

The AGPLv3 license was chosen to keep the project and any forks open for the public good.